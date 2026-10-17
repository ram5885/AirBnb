package com.HotelManagement.AirBnb.payment.gateway;

/** The gateway couldn't be reached or rejected a request. The message is safe to log (no secrets). */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
