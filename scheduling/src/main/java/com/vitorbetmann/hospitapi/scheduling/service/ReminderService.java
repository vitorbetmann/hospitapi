package com.vitorbetmann.hospitapi.scheduling.service;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import com.vitorbetmann.hospitapi.scheduling.domain.AppointmentStatus;
import com.vitorbetmann.hospitapi.scheduling.messaging.AppointmentEvent;
import com.vitorbetmann.hospitapi.scheduling.messaging.AppointmentEventType;
import com.vitorbetmann.hospitapi.scheduling.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ReminderService {

    private final AppointmentRepository appointmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration window;

    public ReminderService(AppointmentRepository appointmentRepository,
                           ApplicationEventPublisher eventPublisher,
                           Clock clock,
                           @Value("${hospitapi.reminders.window}") Duration window) {
        this.appointmentRepository = appointmentRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.window = window;
    }

    /**
     * Claims appointments due within the window and publishes REMINDER_DUE after commit.
     */
    @Transactional
    public int publishDueReminders() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Appointment> due = appointmentRepository.lockDueForReminder(
                AppointmentStatus.SCHEDULED, now, now.plus(window));
        for (Appointment appointment : due) {
            appointment.markReminderSent(now);
            eventPublisher.publishEvent(
                    AppointmentEvent.of(AppointmentEventType.REMINDER_DUE, appointment, clock));
        }
        return due.size();
    }
}