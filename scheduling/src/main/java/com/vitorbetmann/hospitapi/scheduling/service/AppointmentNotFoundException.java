package com.vitorbetmann.hospitapi.scheduling.service;

import lombok.Getter;

@Getter
public class AppointmentNotFoundException extends RuntimeException {

    private final Long appointmentId;

    public AppointmentNotFoundException(Long appointmentId) {
        super("Appointment not found: " + appointmentId);
        this.appointmentId = appointmentId;
    }
}