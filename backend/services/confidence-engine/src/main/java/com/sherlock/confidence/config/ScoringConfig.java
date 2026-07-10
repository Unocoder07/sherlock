package com.sherlock.confidence.config;

import com.sherlock.confidence.domain.Thresholds;
import com.sherlock.confidence.domain.WeightPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the scoring policy + thresholds as beans. For M2 these are the coded
 * defaults (doc 05 §2, §5); M7 will bind them from externalized, versioned
 * configuration so tuning is a config change, not code (doc 05 §9).
 */
@Configuration
public class ScoringConfig {

    @Bean
    public WeightPolicy weightPolicy() {
        return WeightPolicy.defaults();
    }

    @Bean
    public Thresholds thresholds() {
        return Thresholds.defaults();
    }
}
