package com.sherlock.timeline.config;

import com.sherlock.timeline.domain.TimelineProjector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free {@link TimelineProjector} into the context with its
 * inflection threshold from configuration.
 */
@Configuration
public class AppConfig {

    @Bean
    public TimelineProjector timelineProjector(
            @Value("${sherlock.timeline.inflection-threshold:0.15}") double inflectionThreshold) {
        return new TimelineProjector(inflectionThreshold);
    }
}
