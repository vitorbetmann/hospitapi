package com.vitorbetmann.hospitapi.scheduling;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class TestMessagingConfiguration {

    static final String TEST_QUEUE = "test.appointment-events";

    @Bean
    Queue testAppointmentEventsQueue() {
        return new Queue(TEST_QUEUE);
    }

    @Bean
    Binding testAppointmentEventsBinding(Queue testAppointmentEventsQueue,
                                         TopicExchange appointmentsExchange) {
        return BindingBuilder.bind(testAppointmentEventsQueue)
                .to(appointmentsExchange)
                .with("appointment.#");
    }
}