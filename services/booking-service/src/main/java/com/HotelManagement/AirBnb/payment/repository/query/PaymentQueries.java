package com.HotelManagement.AirBnb.payment.repository.query;

/** Native SQL for PaymentRepository and PaymentWebhookEventRepository. */
public final class PaymentQueries {

    private PaymentQueries() {
    }

    public static final String FIND_BY_RAZORPAY_ORDER_ID = """
            SELECT p.* FROM payment p WHERE p.razorpay_order_id = :orderId""";

    public static final String FIND_BY_RAZORPAY_REFUND_ID = """
            SELECT p.* FROM payment p WHERE p.razorpay_refund_id = :refundId""";

    /** The booking a payment belongs to (booking owns the payment_id foreign key). */
    public static final String FIND_BOOKING_ID = """
            SELECT b.booking_id FROM booking b WHERE b.payment_id = :paymentId""";

    public static final String WEBHOOK_EVENT_EXISTS = """
            SELECT EXISTS (SELECT 1 FROM payment_webhook_event e WHERE e.event_id = :eventId)""";
}
