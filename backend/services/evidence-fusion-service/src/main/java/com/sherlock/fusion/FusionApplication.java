package com.sherlock.fusion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Evidence Fusion Engine — the stateless anti-corruption layer between messy AI
 * signals and the clean Confidence Engine. Consumes video/audio signals, anchor
 * consistency signals and meeting events, and emits normalized {@code evidence.events}.
 */
@SpringBootApplication
public class FusionApplication {
    public static void main(String[] args) {
        SpringApplication.run(FusionApplication.class, args);
    }
}
