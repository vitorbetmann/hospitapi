package com.vitorbetmann.hospitapi.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class MessagingConfig {

    public static final String APPOINTMENTS_EXCHANGE = "hospitapi.appointments";
    public static final String APPOINTMENT_ROUTING_PATTERN = "appointment.*";

    public static final String APPOINTMENT_EVENTS_QUEUE = "notification.appointment-events";
    public static final String DEAD_LETTER_EXCHANGE = "notification.dlx";
    public static final String DEAD_LETTER_QUEUE = "notification.appointment-events.dlq";

    @Bean
    TopicExchange appointmentsExchange() {
        return new TopicExchange(APPOINTMENTS_EXCHANGE);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue appointmentEventsQueue() {
        return QueueBuilder.durable(APPOINTMENT_EVENTS_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding appointmentEventsBinding(Queue appointmentEventsQueue, TopicExchange appointmentsExchange) {
        return BindingBuilder.bind(appointmentEventsQueue)
                .to(appointmentsExchange)
                .with(APPOINTMENT_ROUTING_PATTERN);
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_LETTER_QUEUE);
    }

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}