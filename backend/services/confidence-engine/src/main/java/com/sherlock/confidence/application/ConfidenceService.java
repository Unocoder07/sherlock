package com.sherlock.confidence.application;

import com.sherlock.confidence.adapter.out.ConfidenceUpdatePublisher;
import com.sherlock.confidence.adapter.out.RedisVerdictStore;
import com.sherlock.confidence.adapter.out.persistence.ParticipantStateEntity;
import com.sherlock.confidence.adapter.out.persistence.ParticipantStateRepository;
import com.sherlock.confidence.domain.Evidence;
import com.sherlock.confidence.domain.MeetingBelief;
import com.sherlock.confidence.domain.ScoreResult;
import com.sherlock.confidence.domain.Thresholds;
import com.sherlock.confidence.domain.WeightPolicy;
import com.sherlock.contracts.confidence.v1.ParticipantState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the live per-meeting beliefs and drives the decay ticker. Evidence flows
 * in from the Kafka consumer; on a fixed cadence every meeting is re-scored and
 * re-classified, and each state transition is published to
 * {@code confidence.updates} + mirrored to Redis + checkpointed to Postgres.
 *
 * <p>Ticking on a timer (not only on new evidence) is what lets beliefs age:
 * without it a participant whose signals stopped would never reach SIGNAL_LOST.
 */
@Service
public class ConfidenceService {

    private static final Logger log = LoggerFactory.getLogger(ConfidenceService.class);

    private final Map<String, MeetingBelief> meetings = new ConcurrentHashMap<>();

    private final WeightPolicy policy;
    private final Thresholds thresholds;
    private final ConfidenceUpdatePublisher publisher;
    private final RedisVerdictStore redis;
    private final ParticipantStateRepository snapshots;
    private final Clock clock;

    public ConfidenceService(WeightPolicy policy,
                             Thresholds thresholds,
                             ConfidenceUpdatePublisher publisher,
                             RedisVerdictStore redis,
                             ParticipantStateRepository snapshots,
                             Clock clock) {
        this.policy = policy;
        this.thresholds = thresholds;
        this.publisher = publisher;
        this.redis = redis;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    /** Apply one normalized evidence record to the meeting's belief. */
    public void apply(String meetingId, Evidence evidence) {
        meetings.computeIfAbsent(meetingId, MeetingBelief::new).apply(evidence);
    }

    /** Mark a participant as having left (freezes their belief). */
    public void markLeft(String meetingId, String participantId) {
        MeetingBelief mb = meetings.get(meetingId);
        if (mb != null) {
            mb.markLeft(participantId);
        }
    }

    /** Decay + reclassify all meetings; publish/persist every state transition. */
    @Scheduled(fixedDelayString = "${sherlock.tick.interval-ms:1000}")
    public void tick() {
        long now = clock.millis();
        for (MeetingBelief mb : meetings.values()) {
            List<MeetingBelief.VerdictUpdate> changes = mb.tick(now, policy, thresholds);
            for (MeetingBelief.VerdictUpdate u : changes) {
                emit(mb.meetingId(), u, now);
            }
        }
    }

    private void emit(String meetingId, MeetingBelief.VerdictUpdate u, long now) {
        log.info("verdict meeting={} participant={} {} -> {} score={} sep={}",
                meetingId, u.participantId(), u.previousState(), u.newState(),
                String.format("%.3f", u.result().score()), String.format("%.3f", u.separation()));
        publisher.publish(meetingId, u, now);
        redis.write(meetingId, u);
        snapshots.save(new ParticipantStateEntity(
                meetingId, u.participantId(), u.newState().name(),
                u.result().score(), u.separation(), clock.instant()));
    }

    /** Current per-participant verdicts for a meeting (for the REST query). */
    public List<Verdict> verdicts(String meetingId) {
        MeetingBelief mb = meetings.get(meetingId);
        if (mb == null) {
            return List.of();
        }
        long now = clock.millis();
        return mb.participantIds().stream()
                .map(pid -> {
                    ScoreResult r = mb.scoreOf(pid, now, policy, thresholds);
                    return new Verdict(pid, mb.stateOf(pid), r == null ? 0.0 : r.score());
                })
                .toList();
    }

    /** Lightweight read model for the verdict endpoint. */
    public record Verdict(String participantId, ParticipantState state, double score) {
    }
}
