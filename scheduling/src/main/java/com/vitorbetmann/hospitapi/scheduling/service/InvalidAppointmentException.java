package com.vitorbetmann.hospitapi.scheduling.service;

public class InvalidAppointmentException extends RuntimeException {

    public InvalidAppointmentException(String message) {
        super(message);
    }
}