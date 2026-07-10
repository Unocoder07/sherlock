package com.sherlock.explanation.config;

import com.sherlock.explanation.domain.ReasonRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free domain into the Spring context. The
 * {@link ReasonRenderer} is stateless and thread-safe, so a single shared bean
 * serves every consumer thread.
 */
@Configuration
public class AppConfig {

    @Bean
    public ReasonRenderer reasonRenderer() {
        return new ReasonRenderer();
    }
}
