package com.vitorbetmann.hospitapi.notification.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Notification's own copy of the event published by scheduling.
 * The JSON field names are the contract (D-037); the Java type is not shared.
 */
public record AppointmentEvent(
        UUID eventId,
        AppointmentEventType type,
        Long appointmentId,
        AppointmentStatus status,
        String patientName,
        String patientEmail,
        OffsetDateTime scheduledAt,
        OffsetDateTime occurredAt) {
}