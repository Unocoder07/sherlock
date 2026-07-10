package com.sherlock.notification.application;

import com.sherlock.contracts.confidence.v1.ConfidenceUpdate;
import com.sherlock.contracts.confidence.v1.ParticipantState;
import com.sherlock.notification.adapter.out.NotificationPublisher;
import com.sherlock.notification.adapter.out.persistence.NotificationEntity;
import com.sherlock.notification.adapter.out.persistence.NotificationRepository;
import com.sherlock.notification.domain.AlertRules;
import com.sherlock.notification.domain.AlertRules.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates alerting: run a verdict through the {@link AlertRules}, and on a
 * hit persist an audit row, republish it, and log it. Logging is the M4 "channel
 * adapter" — email/webhook adapters plug in here later (doc 02 §8). Persist before
 * publish so a pushed alert always has a durable record.
 */
@Service
public class NotificationAppService {

    private static final Logger log = LoggerFactory.getLogger(NotificationAppService.class);
    private static final String STATE_PREFIX = "PARTICIPANT_STATE_";

    private final AlertRules rules;
    private final NotificationRepository repository;
    private final NotificationPublisher publisher;

    public NotificationAppService(AlertRules rules,
                                  NotificationRepository repository,
                                  NotificationPublisher publisher) {
        this.rules = rules;
        this.repository = repository;
        this.publisher = publisher;
    }

    public void handle(ConfidenceUpdate u) {
        String state = clean(u.getState());
        String prevState = clean(u.getPreviousState());

        Optional<Alert> decision = rules.evaluate(
                u.getMeetingId(), u.getParticipantId(), state, prevState, u.getOccurredAtMs());
        if (decision.isEmpty()) {
            return;
        }
        Alert a = decision.get();

        NotificationEntity entity = new NotificationEntity(
                UUID.randomUUID().toString(),
                u.getMeetingId(),
                u.getParticipantId(),
                a.severity().name(),
                a.rule(),
                a.title(),
                a.message(),
                a.state(),
                u.getOccurredAtMs());

        repository.save(entity);
        publisher.publish(entity);
        log.info("ALERT [{}] {} — meeting={} participant={}",
                a.severity(), a.title(), u.getMeetingId(), u.getParticipantId());
    }

    private static String clean(ParticipantState state) {
        String name = state.name();
        return name.startsWith(STATE_PREFIX) ? name.substring(STATE_PREFIX.length()) : name;
    }
}
