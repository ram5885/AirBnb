package com.HotelManagement.AirBnb.service;

import com.HotelManagement.AirBnb.entity.Booking;
import com.HotelManagement.AirBnb.entity.Payment;
import com.HotelManagement.AirBnb.entity.enums.BookingStatus;
import com.HotelManagement.AirBnb.entity.enums.PaymentStatus;
import com.HotelManagement.AirBnb.exception.ConflictException;
import com.HotelManagement.AirBnb.payment.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cancels a booking that still holds inventory: a paid (online) booking is fully refunded through the gateway
 * first (if the refund can't be started, an exception aborts the whole cancellation so nothing is
 * half-done), then the rooms are released and the booking is marked CANCELLED. No fees are applied.
 * An unpaid pay-at-hotel booking is simply cancelled; one whose payment was collected at the hotel is refused.
 * <p>
 * Callers must hold the booking's row lock and run inside a transaction; the status check under that
 * lock is what prevents double cancellation, double refunds and double inventory release.
 */
@Service
public class BookingCancellationService {

    private static final Logger log = LoggerFactory.getLogger(BookingCancellationService.class);

    private final BookingInventory bookingInventory;
    private final PaymentGateway gateway;

    public BookingCancellationService(BookingInventory bookingInventory, PaymentGateway gateway) {
        this.bookingInventory = bookingInventory;
        this.gateway = gateway;
    }

    public void cancel(Booking booking, String cancelledBy) {
        if (!BookingService.INVENTORY_HOLDING_STATUSES.contains(booking.getBookingStatus())) {
            throw new ConflictException("This booking is already " + booking.getBookingStatus() + ".");
        }
        Payment payment = booking.getPayment();
        if (GuestBookingMapper.collectedAtHotel(booking)) {
            // Money taken at the front desk can't be refunded through the payment gateway.
            throw new ConflictException("Payment for this booking was collected at the hotel, so it has to be "
                    + "cancelled and refunded at the hotel.");
        }
        if (payment != null && payment.getPaymentStatus() == PaymentStatus.CONFIRMED) {
            refundInFull(booking, payment);
        } else if (payment != null && (payment.getPaymentStatus() == PaymentStatus.PENDING
                || payment.getPaymentStatus() == PaymentStatus.FAILED)) {
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
        }
        bookingInventory.release(booking);
        booking.setBookingStatus(BookingStatus.CANCELLED);
        log.info("Booking {} cancelled by {}", booking.getBookingId(), cancelledBy);
    }

    /** Full refund of a verified payment. REFUNDED only once the gateway reports it processed. */
    public void refundInFull(Booking booking, Payment payment) {
        long amount = PaymentService.toSubunits(payment.getAmount());
        PaymentGateway.GatewayRefund refund = gateway.refund(payment.getRazorpayPaymentId(), amount,
                "rf_" + booking.getBookingId());
        payment.setRazorpayRefundId(refund.id());
        payment.setPaymentStatus("processed".equals(refund.status()) ? PaymentStatus.REFUNDED : PaymentStatus.REFUND_PENDING);
        if ("failed".equals(refund.status())) {
            payment.setFailureReason("Refund failed at the payment provider");
            log.error("Refund {} for booking {} failed at the gateway", refund.id(), booking.getBookingId());
        }
    }
}
