package com.vitorbetmann.hospitapi.scheduling.service;

import java.time.OffsetDateTime;

public record CreateAppointmentInput(
        Long patientId, Long doctorId, OffsetDateTime scheduledAt, String reason) {
}