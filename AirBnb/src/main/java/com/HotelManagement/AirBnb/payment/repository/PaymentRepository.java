package com.HotelManagement.AirBnb.repository;

import com.HotelManagement.AirBnb.entity.Payment;
import com.HotelManagement.AirBnb.repository.query.PaymentQueries;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends WriteRepository<Payment, Long> {

    @Query(value = PaymentQueries.FIND_BY_RAZORPAY_ORDER_ID, nativeQuery = true)
    Optional<Payment> findByRazorpayOrderId(@Param("orderId") String orderId);

    @Query(value = PaymentQueries.FIND_BY_RAZORPAY_REFUND_ID, nativeQuery = true)
    Optional<Payment> findByRazorpayRefundId(@Param("refundId") String refundId);

    /** Id of the booking a payment belongs to (Booking owns the payment_id foreign key). */
    @Query(value = PaymentQueries.FIND_BOOKING_ID, nativeQuery = true)
    Optional<Long> findBookingId(@Param("paymentId") Long paymentId);
}
