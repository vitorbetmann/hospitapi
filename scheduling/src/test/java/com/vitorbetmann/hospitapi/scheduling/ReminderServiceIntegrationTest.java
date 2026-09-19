package com.vitorbetmann.hospitapi.scheduling;

import com.vitorbetmann.hospitapi.scheduling.domain.AppointmentStatus;
import com.vitorbetmann.hospitapi.scheduling.messaging.AppointmentEvent;
import com.vitorbetmann.hospitapi.scheduling.messaging.AppointmentEventType;
import com.vitorbetmann.hospitapi.scheduling.repository.AppointmentRepository;
import com.vitorbetmann.hospitapi.scheduling.service.AppointmentService;
import com.vitorbetmann.hospitapi.scheduling.service.CreateAppointmentInput;
import com.vitorbetmann.hospitapi.scheduling.service.ReminderService;
import com.vitorbetmann.hospitapi.scheduling.service.UpdateAppointmentInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.jdbc.Sql;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.vitorbetmann.hospitapi.scheduling.TestMessagingConfiguration.TEST_QUEUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@IntegrationTest
@Sql(scripts = "/sql/test-patient.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@WithUserDetails("nurse")
class ReminderServiceIntegrationTest {

    private static final long TEST_PATIENT_ID = 900L;
    private static final long DOCTOR_ID = 1L;
    private static final ParameterizedTypeReference<AppointmentEvent> EVENT_TYPE =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    ReminderService reminderService;
    @Autowired
    AppointmentService appointmentService;
    @Autowired
    AppointmentRepository appointmentRepository;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    Clock clock;

    @BeforeEach
    void purgeTestQueue() {
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
    }

    @Test
    void claimsScheduledAppointmentInsideWindow() {
        Long id = createAt(inHours(2));
        purgeTestQueue();

        reminderService.publishDueReminders();

        assertThat(reminderEventsFor(id)).hasSize(1);
        assertThat(reminderSentAt(id)).isCloseTo(OffsetDateTime.now(clock), within(1, ChronoUnit.MILLIS));
    }

    @Test
    void skipsAppointmentsOutsideWindowOrNotScheduled() {
        Long later = createAt(inHours(30));
        Long cancelled = createAt(inHours(2));
        appointmentService.update(cancelled, statusChange(AppointmentStatus.CANCELLED));
        purgeTestQueue();

        reminderService.publishDueReminders();

        assertThat(reminderEventsFor(later, cancelled)).isEmpty();
        assertThat(reminderSentAt(later)).isNull();
        assertThat(reminderSentAt(cancelled)).isNull();
    }

    @Test
    void secondRunPublishesNothing() {
        Long id = createAt(inHours(2));
        reminderService.publishDueReminders();
        assertThat(reminderEventsFor(id)).hasSize(1);

        reminderService.publishDueReminders();

        assertThat(reminderEventsFor(id)).isEmpty();
    }

    @Test
    void rescheduleToDifferentInstantMakesItClaimableAgain() {
        OffsetDateTime original = inHours(2);
        Long id = createAt(original);
        reminderService.publishDueReminders();
        assertThat(reminderEventsFor(id)).hasSize(1);

        appointmentService.update(id, reschedule(original.plusHours(1)));
        assertThat(reminderSentAt(id)).isNull();
        purgeTestQueue();

        reminderService.publishDueReminders();

        assertThat(reminderEventsFor(id)).hasSize(1);
    }

    @Test
    void sameInstantWithDifferentOffsetIsNotAReschedule() {
        OffsetDateTime original = inHours(2);
        Long id = createAt(original);
        reminderService.publishDueReminders();
        assertThat(reminderEventsFor(id)).hasSize(1);

        appointmentService.update(id, reschedule(original.withOffsetSameInstant(ZoneOffset.ofHours(-3))));
        assertThat(reminderSentAt(id)).isNotNull();
        purgeTestQueue();

        reminderService.publishDueReminders();

        assertThat(reminderEventsFor(id)).isEmpty();
    }

    /**
     * Truncated to seconds: Postgres keeps microseconds, so a nanosecond value would never compare isEqual after reload.
     */
    private OffsetDateTime inHours(long hours) {
        return OffsetDateTime.now(clock).plusHours(hours).truncatedTo(ChronoUnit.SECONDS);
    }

    private Long createAt(OffsetDateTime scheduledAt) {
        return appointmentService.create(
                        new CreateAppointmentInput(TEST_PATIENT_ID, DOCTOR_ID, scheduledAt, "Reminder job test"))
                .getId();
    }

    private static UpdateAppointmentInput reschedule(OffsetDateTime scheduledAt) {
        return new UpdateAppointmentInput(null, scheduledAt, null, null);
    }

    private static UpdateAppointmentInput statusChange(AppointmentStatus status) {
        return new UpdateAppointmentInput(null, null, status, null);
    }

    private OffsetDateTime reminderSentAt(Long id) {
        return appointmentRepository.findById(id).orElseThrow().getReminderSentAt();
    }

    /**
     * Drains the test queue and keeps only REMINDER_DUE events for the given appointments.
     */
    private List<AppointmentEvent> reminderEventsFor(Long... ids) {
        Set<Long> wanted = Set.of(ids);
        List<AppointmentEvent> reminders = new ArrayList<>();
        AppointmentEvent event;
        while ((event = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 1_000, EVENT_TYPE)) != null) {
            if (event.type() == AppointmentEventType.REMINDER_DUE && wanted.contains(event.appointmentId())) {
                reminders.add(event);
            }
        }
        return reminders;
    }
}