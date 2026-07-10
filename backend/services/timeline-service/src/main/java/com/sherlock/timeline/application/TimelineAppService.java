package com.sherlock.timeline.application;

import com.sherlock.contracts.explanation.v1.EnrichedVerdict;
import com.sherlock.timeline.adapter.out.TimelineEventPublisher;
import com.sherlock.timeline.adapter.out.persistence.TimelineEntryEntity;
import com.sherlock.timeline.adapter.out.persistence.TimelineEntryRepository;
import com.sherlock.timeline.domain.TimelineProjector;
import com.sherlock.timeline.domain.TimelineProjector.Projected;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the timeline projection: decide (via {@link TimelineProjector})
 * whether an enriched verdict earns an entry, then persist it (append-only) and
 * republish it for live push. Persist-before-publish so a crash can't leave a
 * pushed entry with no durable record.
 */
@Service
public class TimelineAppService {

    private final TimelineProjector projector;
    private final TimelineEntryRepository repository;
    private final TimelineEventPublisher publisher;

    public TimelineAppService(TimelineProjector projector,
                              TimelineEntryRepository repository,
                              TimelineEventPublisher publisher) {
        this.projector = projector;
        this.repository = repository;
        this.publisher = publisher;
    }

    /** Project a verdict; on a hit, append a row and push it. */
    public void record(EnrichedVerdict v) {
        Optional<Projected> decision = projector.project(
                v.getMeetingId(), v.getParticipantId(), v.getState(), v.getPreviousState(), v.getScore());
        if (decision.isEmpty()) {
            return;
        }
        Projected p = decision.get();

        TimelineEntryEntity entity = new TimelineEntryEntity(
                UUID.randomUUID().toString(),
                v.getMeetingId(),
                v.getParticipantId(),
                p.kind().name(),
                p.fromState(),
                p.toState(),
                p.score(),
                headline(p, v),
                detail(v),
                v.getOccurredAtMs());

        repository.save(entity);
        publisher.publish(entity);
    }

    /** Transition entries reuse the verdict's headline; inflections describe the move. */
    private static String headline(Projected p, EnrichedVerdict v) {
        if (p.kind() == TimelineProjector.Kind.SCORE_INFLECTION) {
            return "Confidence " + (v.getScore() >= 0.5 ? "strengthened" : "weakened");
        }
        return v.getHeadline();
    }

    /** Detail = the strongest rendered reason, if any. */
    private static String detail(EnrichedVerdict v) {
        return v.getReasonsCount() > 0 ? v.getReasons(0).getText() : "";
    }
}
