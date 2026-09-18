package com.vitorbetmann.hospitapi.scheduling.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class MessagingConfig {

    public static final String APPOINTMENTS_EXCHANGE = "hospitapi.appointments";

    @Bean
    TopicExchange appointmentsExchange() {
        return new TopicExchange(APPOINTMENTS_EXCHANGE);
    }

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}