package com.ordernest.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordernest.notification.dto.SendEmailRequest;
import com.ordernest.notification.event.OrderStatusChangedEvent;
import com.ordernest.notification.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusChangedEmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusChangedEmailConsumer.class);

    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final String orderStatusEventsTopic;

    public OrderStatusChangedEmailConsumer(
        ObjectMapper objectMapper,
        EmailService emailService,
        @Value("${app.kafka.topic.order-status-events:order.status.events}") String orderStatusEventsTopic
    ) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.orderStatusEventsTopic = orderStatusEventsTopic;
    }

    @KafkaListener(
        topics = "${app.kafka.topic.order-status-events:order.status.events}",
        groupId = "${app.kafka.consumer.order-status-group-id:notification-service-order-status}"
    )
    public void consume(String payload) {
        final OrderStatusChangedEvent event;
        try {
            event = objectMapper.readValue(payload, OrderStatusChangedEvent.class);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse order status event payload from topic={}", orderStatusEventsTopic, ex);
            return;
        }

        if (isBlank(event.userEmail()) || isBlank(event.currentStatus())) {
            log.warn("Skipping invalid order status event payload from topic={}", orderStatusEventsTopic);
            return;
        }

        String subject = "Order " + event.orderId() + " status: " + event.currentStatus();
        String body = """
            <p>Your order status has changed.</p>
            <p><b>Order ID:</b> %s</p>
            <p><b>Previous status:</b> %s</p>
            <p><b>Current status:</b> %s</p>
            <p><b>Payment status:</b> %s</p>
            <p><b>Shipment status:</b> %s</p>
            <p><b>Reason:</b> %s</p>
            <p><b>Product:</b> %s</p>
            <p><b>Quantity:</b> %s</p>
            <p><b>Total amount:</b> %s %s</p>
            """.formatted(
                event.orderId(),
                blankAsNA(event.previousStatus()),
                event.currentStatus(),
                blankAsNA(event.paymentStatus()),
                blankAsNA(event.shipmentStatus()),
                blankAsNA(event.reason()),
                blankAsNA(event.productName()),
                event.quantity(),
                event.totalAmount(),
                blankAsNA(event.currency())
            );

        emailService.send(new SendEmailRequest(event.userEmail(), subject, body));
        log.info("Processed order status event for email recipient={}, orderId={}", event.userEmail(), event.orderId());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankAsNA(String value) {
        return isBlank(value) ? "N/A" : value;
    }
}
