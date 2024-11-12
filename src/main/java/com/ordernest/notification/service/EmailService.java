package com.ordernest.notification.service;

import com.ordernest.notification.dto.SendEmailRequest;
import com.ordernest.notification.exception.NotificationException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final Resend resend;
    private final String fromEmail;

    public EmailService(
        @Value("${app.notification.resend.api-key}") String resendApiKey,
        @Value("${app.notification.from-email}") String fromEmail
    ) {
        this.resend = new Resend(resendApiKey);
        this.fromEmail = fromEmail;
    }

    public void send(SendEmailRequest request) {
        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(request.to())
                .subject(request.subject())
                .html(request.body())
                .build();
            resend.emails().send(params);
        } catch (ResendException ex) {
            throw new NotificationException("Failed to send email", ex);
        }
    }
}
