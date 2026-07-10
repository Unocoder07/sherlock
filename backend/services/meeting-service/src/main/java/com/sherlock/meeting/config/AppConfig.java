package com.sherlock.meeting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Cross-cutting beans. A single injected {@link Clock} makes time deterministic
 * and testable — no domain/application code calls {@code Instant.now()} directly.
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
