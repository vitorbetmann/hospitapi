package com.vitorbetmann.hospitapi.scheduling;

import static com.vitorbetmann.hospitapi.scheduling.TestMessagingConfiguration.TEST_QUEUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vitorbetmann.hospitapi.scheduling.domain.Appointment;
import com.vitorbetmann.hospitapi.scheduling.domain.AppointmentStatus;
import com.vitorbetmann.hospitapi.scheduling.messaging.AppointmentEvent;
import com.vitorbetmann.hospitapi.scheduling.messaging.AppointmentEventType;
import com.vitorbetmann.hospitapi.scheduling.service.AppointmentService;
import com.vitorbetmann.hospitapi.scheduling.service.CreateAppointmentInput;
import com.vitorbetmann.hospitapi.scheduling.service.InvalidAppointmentException;
import com.vitorbetmann.hospitapi.scheduling.service.UpdateAppointmentInput;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@Sql(scripts = "/sql/test-patient.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@WithUserDetails("nurse")
class AppointmentEventPublishingTest {

    private static final long TEST_PATIENT_ID = 900L;
    private static final long SEEDED_DOCTOR_ID = 1L;

    @Autowired
    AppointmentService appointmentService;
    @Autowired
    TransactionTemplate transactionTemplate;
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
    void createPublishesCreatedEventOnlyAfterCommit() {
        AtomicLong id = new AtomicLong();

        transactionTemplate.executeWithoutResult(tx -> {
            id.set(appointmentService.create(newAppointmentInput()).getId());
            assertThat(rabbitTemplate.receive(TEST_QUEUE, 500)).isNull();
        });

        AppointmentEvent event = receiveEvent();
        assertThat(event.type()).isEqualTo(AppointmentEventType.CREATED);
        assertThat(event.appointmentId()).isEqualTo(id.get());
        assertThat(event.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(event.patientEmail()).isNotBlank();
        assertThat(event.occurredAt().toInstant()).isEqualTo(clock.instant());
    }

    @Test
    void rolledBackCreatePublishesNothing() {
        transactionTemplate.executeWithoutResult(tx -> {
            appointmentService.create(newAppointmentInput());
            tx.setRollbackOnly();
        });

        assertThat(rabbitTemplate.receive(TEST_QUEUE, 1000)).isNull();
    }

    @Test
    void updatePublishesUpdatedEventWithNewValues() {
        Appointment created = appointmentService.create(newAppointmentInput());
        amqpAdmin.purgeQueue(TEST_QUEUE, false);

        OffsetDateTime newTime = OffsetDateTime.now(clock).plusDays(5);
        appointmentService.update(created.getId(), updateInput(newTime, null));

        AppointmentEvent event = receiveEvent();
        assertThat(event.type()).isEqualTo(AppointmentEventType.UPDATED);
        assertThat(event.appointmentId()).isEqualTo(created.getId());
        assertThat(event.scheduledAt().toInstant()).isEqualTo(newTime.toInstant());
    }

    @Test
    void rejectedUpdatePublishesNothing() {
        Appointment created = appointmentService.create(newAppointmentInput());
        appointmentService.update(created.getId(), updateInput(null, AppointmentStatus.CANCELLED));
        amqpAdmin.purgeQueue(TEST_QUEUE, false);

        assertThatThrownBy(() -> appointmentService.update(created.getId(), updateInput(null, AppointmentStatus.SCHEDULED)))
                .isInstanceOf(InvalidAppointmentException.class);

        assertThat(rabbitTemplate.receive(TEST_QUEUE, 1000)).isNull();
    }

    private AppointmentEvent receiveEvent() {
        AppointmentEvent event = rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000,
                new ParameterizedTypeReference<AppointmentEvent>() {
                });
        assertThat(event).as("expected an event on %s", TEST_QUEUE).isNotNull();
        return event;
    }

    private CreateAppointmentInput newAppointmentInput() {
        return new CreateAppointmentInput(TEST_PATIENT_ID, SEEDED_DOCTOR_ID,
                OffsetDateTime.now(clock).plusDays(2), "Messaging test");
    }

    private UpdateAppointmentInput updateInput(OffsetDateTime scheduledAt, AppointmentStatus status) {
        return new UpdateAppointmentInput(null, scheduledAt, status, null);
    }
}