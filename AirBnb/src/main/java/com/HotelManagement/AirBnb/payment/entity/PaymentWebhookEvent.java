package com.HotelManagement.AirBnb.payment.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Razorpay webhook deliveries already processed (keyed by the x-razorpay-event-id header), for idempotency. */
@Entity
@Table(name = "payment_webhook_event")
@Getter
@Setter
public class PaymentWebhookEvent {

    @Id
    @Column(length = 100)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime receivedAt;
}
