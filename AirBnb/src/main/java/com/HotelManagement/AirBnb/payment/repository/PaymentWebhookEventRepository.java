package com.HotelManagement.AirBnb.repository;

import com.HotelManagement.AirBnb.entity.PaymentWebhookEvent;
import com.HotelManagement.AirBnb.repository.query.PaymentQueries;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentWebhookEventRepository extends WriteRepository<PaymentWebhookEvent, String> {

    @Query(value = PaymentQueries.WEBHOOK_EVENT_EXISTS, nativeQuery = true)
    boolean existsByEventId(@Param("eventId") String eventId);
}
