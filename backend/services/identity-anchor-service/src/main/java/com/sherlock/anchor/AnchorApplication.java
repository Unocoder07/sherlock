package com.sherlock.anchor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Identity Anchor Service (MOCK for M2). Consumes video/audio signals and emits
 * the anchor lifecycle + per-window consistency signals to {@code identity.anchor}.
 * Replaced by the real online-clustering service in M6.5 behind the same contract.
 */
@SpringBootApplication
public class AnchorApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnchorApplication.class, args);
    }
}
