package com.vitorbetmann.hospitapi.scheduling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full application context backed by Testcontainers, a shifted test Clock,
 * and a test queue bound to the appointments exchange.
 * Every integration test uses this so they all share one cached context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureGraphQlTester
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class, TestMessagingConfiguration.class})
@interface IntegrationTest {
}