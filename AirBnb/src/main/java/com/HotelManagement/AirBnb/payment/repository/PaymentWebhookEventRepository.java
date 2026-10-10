package com.HotelManagement.AirBnb.payment.repository;

import com.HotelManagement.AirBnb.payment.entity.PaymentWebhookEvent;
import com.HotelManagement.AirBnb.payment.repository.query.PaymentQueries;
import com.HotelManagement.AirBnb.shared.repository.WriteRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentWebhookEventRepository extends WriteRepository<PaymentWebhookEvent, String> {

    @Query(value = PaymentQueries.WEBHOOK_EVENT_EXISTS, nativeQuery = true)
    boolean existsByEventId(@Param("eventId") String eventId);
}
