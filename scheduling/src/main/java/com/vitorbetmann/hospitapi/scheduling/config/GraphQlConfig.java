package com.vitorbetmann.hospitapi.scheduling.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.Clock;

@Configuration
public class GraphQlConfig {

    @Bean
    RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiring -> wiring.scalar(ExtendedScalars.DateTime);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}