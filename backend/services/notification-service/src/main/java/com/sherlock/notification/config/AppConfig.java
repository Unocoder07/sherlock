package com.sherlock.notification.config;

import com.sherlock.notification.domain.AlertRules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free {@link AlertRules} into the context with its throttle
 * cooldown from configuration.
 */
@Configuration
public class AppConfig {

    @Bean
    public AlertRules alertRules(@Value("${sherlock.alerts.cooldown-ms:30000}") long cooldownMs) {
        return new AlertRules(cooldownMs);
    }
}
