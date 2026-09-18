package com.vitorbetmann.hospitapi.notification.reminder;

import com.vitorbetmann.hospitapi.notification.messaging.AppointmentEvent;

public interface ReminderSender {
    void send(AppointmentEvent event);
}