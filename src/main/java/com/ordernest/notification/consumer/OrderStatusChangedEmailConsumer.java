package com.ordernest.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordernest.notification.dto.SendEmailRequest;
import com.ordernest.notification.event.OrderStatusChangedEvent;
import com.ordernest.notification.service.EmailService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
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
    private final String orderDetailsBaseUrl;

    public OrderStatusChangedEmailConsumer(
        ObjectMapper objectMapper,
        EmailService emailService,
        @Value("${app.kafka.topic.order-status-events:order.status.events}") String orderStatusEventsTopic,
        @Value("${app.web.order-details-base-url:}") String orderDetailsBaseUrl
    ) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.orderStatusEventsTopic = orderStatusEventsTopic;
        this.orderDetailsBaseUrl = orderDetailsBaseUrl;
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

        String subject = resolveSubject(event);
        String body = buildOrderEmail(event);

        emailService.send(new SendEmailRequest(event.userEmail(), subject, body));
        log.info("Processed order status event for email recipient={}, orderId={}", event.userEmail(), event.orderId());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankAsNA(String value) {
        return isBlank(value) ? "N/A" : value;
    }

    private String resolveSubject(OrderStatusChangedEvent event) {
        if ("CREATED".equals(event.currentStatus())) {
            return "Order placed successfully - payment pending";
        }
        return "Order update: " + event.currentStatus();
    }

    private String buildOrderEmail(OrderStatusChangedEvent event) {
        boolean orderCreated = "CREATED".equals(event.currentStatus());
        String title = orderCreated ? "Your order has been placed" : "Your order status was updated";
        String intro = orderCreated
            ? "We have received your order. Please complete the payment to confirm it."
            : "There is a new update on your OrderNest order.";
        String actionHtml = buildActionHtml(event);

        return """
            <!doctype html>
            <html>
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#111827;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:24px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border:1px solid #e5e7eb;border-radius:14px;overflow:hidden;">
                      <tr>
                        <td style="background:#0f766e;padding:24px 28px;color:#ffffff;">
                          <p style="margin:0 0 8px 0;font-size:13px;letter-spacing:.08em;text-transform:uppercase;">OrderNest</p>
                          <h1 style="margin:0;font-size:24px;line-height:1.25;">%s</h1>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:24px 28px;">
                          <p style="margin:0 0 18px 0;font-size:15px;line-height:1.6;color:#374151;">%s</p>
                          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f9fafb;border-radius:12px;padding:16px;">
                            %s
                            %s
                            %s
                            %s
                            %s
                            %s
                          </table>
                          %s
                          <p style="margin:22px 0 0 0;font-size:12px;line-height:1.5;color:#6b7280;">Order ID: %s</p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(
                escapeHtml(title),
                escapeHtml(intro),
                detailRow("Product", event.productName()),
                detailRow("Quantity", event.quantity() == null ? "N/A" : event.quantity().toString()),
                detailRow("Total", formatAmount(event.totalAmount(), event.currency())),
                detailRow("Order status", event.currentStatus()),
                detailRow("Payment status", event.paymentStatus()),
                detailRow("Shipment status", event.shipmentStatus()),
                actionHtml,
                escapeHtml(event.orderId())
            );
    }

    private String buildActionHtml(OrderStatusChangedEvent event) {
        if (!"CREATED".equals(event.currentStatus()) || isBlank(orderDetailsBaseUrl)) {
            return "";
        }

        String orderUrl = orderDetailsBaseUrl.endsWith("/")
            ? orderDetailsBaseUrl + escapeHtml(event.orderId())
            : orderDetailsBaseUrl + "/" + escapeHtml(event.orderId());

        return """
            <p style="margin:22px 0 0 0;">
              <a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:11px 16px;border-radius:8px;font-weight:700;font-size:14px;">Complete Payment</a>
            </p>
            """.formatted(orderUrl);
    }

    private String detailRow(String label, String value) {
        return """
            <tr>
              <td style="padding:8px 0;color:#6b7280;font-size:14px;">%s</td>
              <td align="right" style="padding:8px 0;color:#111827;font-size:14px;font-weight:700;">%s</td>
            </tr>
            """.formatted(escapeHtml(label), escapeHtml(blankAsNA(value)));
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return blankAsNA(currency);
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(amount) + " " + blankAsNA(currency);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
