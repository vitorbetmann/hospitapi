package com.vitorbetmann.hospitapi.scheduling.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "hospitapi.reminders.enabled", matchIfMissing = true)
public class SchedulingConfig {
}