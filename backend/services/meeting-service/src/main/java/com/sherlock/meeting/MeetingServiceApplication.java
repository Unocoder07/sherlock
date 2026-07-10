package com.sherlock.meeting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Meeting Service — owns the meeting lifecycle + participant roster and publishes
 * {@code meeting.events} via a transactional outbox. Entry point for the app.
 */
@SpringBootApplication
@EnableScheduling   // drives the outbox relay poller
public class MeetingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MeetingServiceApplication.class, args);
    }
}
