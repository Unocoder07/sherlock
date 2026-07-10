package com.sherlock.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification Service (M4): reacts to state transitions of interest and raises
 * alerts (doc 02 §8). Consumes {@code confidence.updates}, applies alert rules
 * with throttling, persists an audit trail, and republishes on {@code notifications}.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
