package com.ordernest.notification.controller;

import com.ordernest.notification.dto.MessageResponse;
import com.ordernest.notification.dto.SendEmailRequest;
import com.ordernest.notification.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final EmailService emailService;

    public NotificationController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/email")
    public ResponseEntity<MessageResponse> sendEmail(@Valid @RequestBody SendEmailRequest request) {
        emailService.send(request);
        return ResponseEntity.ok(new MessageResponse("Email sent successfully"));
    }
}
