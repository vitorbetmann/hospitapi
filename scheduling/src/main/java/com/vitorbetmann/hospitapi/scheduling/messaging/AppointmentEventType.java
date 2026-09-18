package com.vitorbetmann.hospitapi.scheduling.messaging;

public enum AppointmentEventType {
    CREATED("appointment.created"),
    UPDATED("appointment.updated");

    private final String routingKey;

    AppointmentEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}