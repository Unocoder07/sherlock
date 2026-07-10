package com.sherlock.timeline.adapter.in.rest;

import com.sherlock.timeline.adapter.out.persistence.TimelineEntryEntity;
import com.sherlock.timeline.adapter.out.persistence.TimelineEntryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-side HTTP surface: a meeting's persisted timeline in chronological order.
 * The live view rides {@code timeline.events} over WebSocket; this endpoint backs
 * history queries and lets a dashboard hydrate on load.
 */
@RestController
@RequestMapping("/meetings/{meetingId}/timeline")
public class TimelineController {

    private final TimelineEntryRepository repository;

    public TimelineController(TimelineEntryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<TimelineEntryDto> timeline(@PathVariable String meetingId) {
        return repository.findByMeetingIdOrderByOccurredAtMsAsc(meetingId).stream()
                .map(TimelineController::toDto)
                .toList();
    }

    private static TimelineEntryDto toDto(TimelineEntryEntity e) {
        return new TimelineEntryDto(
                e.getEntryId(), e.getParticipantId(), e.getKind(), e.getFromState(), e.getToState(),
                e.getScore(), e.getHeadline(), e.getDetail(), e.getOccurredAtMs());
    }

    /** Browser-facing timeline row (kept separate from the JPA entity). */
    public record TimelineEntryDto(
            String entryId,
            String participantId,
            String kind,
            String fromState,
            String toState,
            double score,
            String headline,
            String detail,
            long occurredAtMs) {
    }
}
