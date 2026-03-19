package com.ordernest.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordernest.notification.dto.SendEmailRequest;
import com.ordernest.notification.event.EmailNotificationEvent;
import com.ordernest.notification.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuthEmailEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuthEmailEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final String emailEventsTopic;

    public AuthEmailEventConsumer(
        ObjectMapper objectMapper,
        EmailService emailService,
        @Value("${app.kafka.topic.email-events:notification.email.events}") String emailEventsTopic
    ) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.emailEventsTopic = emailEventsTopic;
    }

    @KafkaListener(
        topics = "${app.kafka.topic.email-events:notification.email.events}",
        groupId = "${spring.kafka.consumer.group-id:notification-service}"
    )
    public void consume(String payload) {
        final EmailNotificationEvent event;
        try {
            event = objectMapper.readValue(payload, EmailNotificationEvent.class);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse email event payload from topic={}", emailEventsTopic, ex);
            return;
        }

        if (isBlank(event.to()) || isBlank(event.subject()) || isBlank(event.body())) {
            log.warn("Skipping invalid email event payload from topic={}", emailEventsTopic);
            return;
        }

        emailService.send(new SendEmailRequest(event.to(), event.subject(), event.body()));
        log.info("Processed email event={}, recipient={}", event.eventType(), event.to());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
