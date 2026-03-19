package com.ordernest.notification.event;

import java.time.Instant;

public record AuthEmailEvent(
    String to,
    String subject,
    String body,
    String eventType,
    Instant timestamp
) {}
