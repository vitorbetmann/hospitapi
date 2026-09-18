package com.vitorbetmann.hospitapi.scheduling.messaging;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import com.vitorbetmann.hospitapi.scheduling.domain.AppointmentStatus;
import com.vitorbetmann.hospitapi.scheduling.domain.Patient;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentEvent(
        UUID eventId,
        AppointmentEventType type,
        Long appointmentId,
        AppointmentStatus status,
        String patientName,
        String patientEmail,
        OffsetDateTime scheduledAt,
        OffsetDateTime occurredAt) {

    public static AppointmentEvent of(AppointmentEventType type, Appointment appointment, Clock clock) {
        Patient patient = appointment.getPatient();
        return new AppointmentEvent(
                UUID.randomUUID(),
                type,
                appointment.getId(),
                appointment.getStatus(),
                patient.getName(),
                patient.getEmail(),
                appointment.getScheduledAt(),
                OffsetDateTime.now(clock));
    }
}