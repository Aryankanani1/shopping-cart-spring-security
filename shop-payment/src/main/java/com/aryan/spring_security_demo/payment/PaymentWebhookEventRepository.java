package com.aryan.spring_security_demo.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    boolean existsByEventId(String eventId);
}
