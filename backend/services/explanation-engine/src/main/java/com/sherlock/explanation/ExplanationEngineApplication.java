package com.sherlock.explanation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Explanation Engine (M4): turns the Confidence Engine's numeric contribution
 * breakdown into human-readable reasons and republishes an enriched verdict for
 * the WS gateway. Stateless presentation logic (doc 02 §7).
 */
@SpringBootApplication
public class ExplanationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExplanationEngineApplication.class, args);
    }
}
