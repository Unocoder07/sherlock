package com.sherlock.confidence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Confidence Engine — the stateful scorer. Consumes {@code evidence.events},
 * maintains a per-participant belief with time decay + hysteresis, and emits
 * verdicts to {@code confidence.updates}. Scheduling drives the decay ticker so
 * beliefs age and can transition to SIGNAL_LOST even without new evidence.
 */
@SpringBootApplication
@EnableScheduling
public class ConfidenceEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfidenceEngineApplication.class, args);
    }
}
