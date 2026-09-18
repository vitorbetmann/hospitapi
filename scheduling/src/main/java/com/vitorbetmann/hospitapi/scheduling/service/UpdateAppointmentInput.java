package com.vitorbetmann.hospitapi.scheduling.service;

import com.vitorbetmann.hospitapi.scheduling.domain.AppointmentStatus;

import java.time.OffsetDateTime;

public record UpdateAppointmentInput(
        Long doctorId, OffsetDateTime scheduledAt, AppointmentStatus status, String reason) {
}