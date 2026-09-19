package com.vitorbetmann.hospitapi.scheduling.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJob {

    private final ReminderService reminderService;

    @Scheduled(fixedDelayString = "${hospitapi.reminders.interval}")
    public void run() {
        int published = reminderService.publishDueReminders();
        if (published > 0) {
            log.info("Published {} REMINDER_DUE event(s)", published);
        }
    }
}