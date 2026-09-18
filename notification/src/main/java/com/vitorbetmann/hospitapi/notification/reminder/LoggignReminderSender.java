package com.vitorbetmann.hospitapi.notification.reminder;

import com.vitorbetmann.hospitapi.notification.messaging.AppointmentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingReminderSender implements ReminderSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingReminderSender.class);

    @Override
    public void send(AppointmentEvent event) {
        log.info("Reminder sent to {} <{}>: appointment {} at {} (event {}, {})",
                event.patientName(), event.patientEmail(), event.appointmentId(),
                event.scheduledAt(), event.eventId(), event.type());
    }
}