package com.vitorbetmann.hospitapi.notification.messaging;

import com.vitorbetmann.hospitapi.notification.config.MessagingConfig;
import com.vitorbetmann.hospitapi.notification.reminder.ReminderSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class AppointmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventListener.class);

    private final ReminderSender reminderSender;

    AppointmentEventListener(ReminderSender reminderSender) {
        this.reminderSender = reminderSender;
    }

    @RabbitListener(queues = MessagingConfig.APPOINTMENT_EVENTS_QUEUE)
    void onAppointmentEvent(AppointmentEvent event) {
        if (event.status() != AppointmentStatus.SCHEDULED) {
            log.debug("Skipping reminder for appointment {} with status {}",
                    event.appointmentId(), event.status());
            return;
        }
        reminderSender.send(event);
    }
}