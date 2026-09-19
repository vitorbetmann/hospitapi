package com.vitorbetmann.hospitapi.scheduling.messaging;

public enum AppointmentEventType {
    CREATED("appointment.created"),
    UPDATED("appointment.updated"),
    REMINDER_DUE("appointment.reminder-due");

    private final String routingKey;

    AppointmentEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}