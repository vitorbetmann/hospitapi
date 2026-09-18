package com.vitorbetmann.hospitapi.scheduling.messaging;

import com.vitorbetmann.hospitapi.scheduling.config.MessagingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class AppointmentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    AppointmentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publish(AppointmentEvent event) {
        try {
            rabbitTemplate.convertAndSend(MessagingConfig.APPOINTMENTS_EXCHANGE,
                    event.type().routingKey(), event);
        } catch (RuntimeException e) {
            log.error("Failed to publish {} event {} for appointment {}",
                    event.type(), event.eventId(), event.appointmentId(), e);
        }
    }
}