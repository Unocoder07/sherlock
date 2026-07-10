package com.sherlock.common.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;

/**
 * Auto-configuration contributed by the common-kafka library. A service that
 * depends on this module and configures {@code spring.kafka} gets a ready
 * {@link EventPublisher} (tagged with {@code ${spring.application.name}@<version>})
 * and a UTC {@link Clock} — no per-service wiring. Registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class SherlockKafkaAutoConfiguration {

    /** Service version tag baked into every produced envelope's {@code producer}. */
    private static final String VERSION = "0.1.0";

    @Bean
    @ConditionalOnMissingBean
    public Clock sherlockClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(KafkaTemplate<String, byte[]> template,
                                         Clock clock,
                                         @Value("${spring.application.name:unknown}") String appName) {
        return new EventPublisher(template, appName + "@" + VERSION, clock);
    }
}
