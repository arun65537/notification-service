package com.ordernest.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordernest.notification.dto.SendEmailRequest;
import com.ordernest.notification.event.SsoActionEvent;
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
    private final String ssoActionEventsTopic;

    public AuthEmailEventConsumer(
        ObjectMapper objectMapper,
        EmailService emailService,
        @Value("${app.kafka.topic.sso-action-events:sso.action.event}") String ssoActionEventsTopic
    ) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.ssoActionEventsTopic = ssoActionEventsTopic;
    }

    @KafkaListener(
        topics = "${app.kafka.topic.sso-action-events:sso.action.event}",
        groupId = "${app.kafka.consumer.sso-action-group-id:notification-service-sso-action}"
    )
    public void consume(String payload) {
        final SsoActionEvent event;
        try {
            event = objectMapper.readValue(payload, SsoActionEvent.class);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse sso action event payload from topic={}", ssoActionEventsTopic, ex);
            return;
        }

        if (isBlank(event.to()) || isBlank(event.eventType())) {
            log.warn("Skipping invalid sso action event payload from topic={}", ssoActionEventsTopic);
            return;
        }

        String subject = resolveSubject(event);
        String body = resolveBody(event);
        emailService.send(new SendEmailRequest(event.to(), subject, body));
        log.info("Processed email event={}, recipient={}", event.eventType(), event.to());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveSubject(SsoActionEvent event) {
        return switch (event.eventType()) {
            case "EMAIL_VERIFICATION_REQUESTED" -> "Verify your OrderNest account";
            case "PASSWORD_RESET_REQUESTED" -> "Reset your OrderNest password";
            case "EMAIL_VERIFIED" -> "Your email is verified";
            case "PASSWORD_CHANGED" -> "Your password was changed";
            default -> "OrderNest account update";
        };
    }

    private String resolveBody(SsoActionEvent event) {
        return switch (event.eventType()) {
            case "EMAIL_VERIFICATION_REQUESTED" ->
                buildActionTemplate(
                    "Confirm your email",
                    "Thanks for signing up. Please verify your email address to activate your account.",
                    "Verify Email",
                    event.actionUrl(),
                    event.expiryMinutes()
                );
            case "PASSWORD_RESET_REQUESTED" ->
                buildActionTemplate(
                    "Password reset request",
                    "We received a request to reset your password.",
                    "Reset Password",
                    event.actionUrl(),
                    event.expiryMinutes()
                );
            case "EMAIL_VERIFIED" ->
                "<p>Your email has been verified successfully.</p>";
            case "PASSWORD_CHANGED" ->
                "<p>Your password was changed successfully. If this wasn't you, reset it immediately.</p>";
            default ->
                "<p>Account action completed.</p>";
        };
    }

    private String buildActionTemplate(
        String title,
        String message,
        String actionLabel,
        String actionUrl,
        Long expiryMinutes
    ) {
        String safeUrl = isBlank(actionUrl) ? "#" : actionUrl;
        String expiryLine = expiryMinutes == null ? "" : "<p>This link expires in " + expiryMinutes + " minutes.</p>";
        return """
            <!doctype html>
            <html>
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:24px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border-radius:12px;padding:24px;">
                      <tr><td><h2 style="margin:0 0 12px 0;">%s</h2></td></tr>
                      <tr><td><p>%s</p>%s</td></tr>
                      <tr><td style="padding-top:12px;"><a href="%s" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;padding:10px 14px;border-radius:8px;">%s</a></td></tr>
                      <tr><td style="padding-top:12px;font-size:12px;color:#6b7280;word-break:break-all;">%s</td></tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(title, message, expiryLine, safeUrl, actionLabel, safeUrl);
    }
}
