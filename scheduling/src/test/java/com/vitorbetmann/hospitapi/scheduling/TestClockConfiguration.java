package com.vitorbetmann.hospitapi.scheduling;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

    // Seed dates are relative to migration time. Shifting "now" by a day moves
    // appointment 3 (+20h) into the past while appointment 1 (+3d) stays in
    // the future, which only happens if the service uses the injected Clock.
    @Bean
    @Primary
    Clock testClock() {
        return Clock.fixed(Instant.now().plus(Duration.ofDays(1)), ZoneOffset.UTC);
    }
}