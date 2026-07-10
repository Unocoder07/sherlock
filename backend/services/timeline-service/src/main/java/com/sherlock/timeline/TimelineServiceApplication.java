package com.sherlock.timeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Timeline Service (M4): maintains an immutable, ordered history of what happened
 * and why (doc 02 §6). Consumes the enriched verdict stream, persists append-only
 * entries, and republishes them on {@code timeline.events} for live push.
 */
@SpringBootApplication
public class TimelineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimelineServiceApplication.class, args);
    }
}
