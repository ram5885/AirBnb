package com.HotelManagement.AirBnb.payment.entity;

import com.HotelManagement.AirBnb.payment.entity.PaymentMethod;
import com.HotelManagement.AirBnb.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Our reference for this payment, sent to the gateway as the order receipt.
    @Column(unique = true, nullable = false)
    private String transactionId;

    @Column(unique = true)
    private String razorpayOrderId;

    // Set once a payment is verified; unique so the same gateway payment can't confirm twice.
    @Column(unique = true)
    private String razorpayPaymentId;

    private String razorpayRefundId;

    @Column(length = 3)
    private String currency;

    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // ONLINE (Razorpay) or PAY_AT_HOTEL; null on records created before this column existed means ONLINE.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod method;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
