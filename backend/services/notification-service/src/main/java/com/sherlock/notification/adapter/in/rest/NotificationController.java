package com.sherlock.notification.adapter.in.rest;

import com.sherlock.notification.adapter.out.persistence.NotificationEntity;
import com.sherlock.notification.adapter.out.persistence.NotificationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-side HTTP surface: a meeting's alert audit trail, most recent first. The
 * live banner rides the {@code notifications} topic over WebSocket; this endpoint
 * backs history queries.
 */
@RestController
@RequestMapping("/meetings/{meetingId}/notifications")
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<NotificationDto> notifications(@PathVariable String meetingId) {
        return repository.findByMeetingIdOrderByOccurredAtMsDesc(meetingId).stream()
                .map(NotificationController::toDto)
                .toList();
    }

    private static NotificationDto toDto(NotificationEntity e) {
        return new NotificationDto(
                e.getNotificationId(), e.getParticipantId(), e.getSeverity(), e.getRule(),
                e.getTitle(), e.getMessage(), e.getState(), e.getOccurredAtMs());
    }

    /** Browser-facing alert row (kept separate from the JPA entity). */
    public record NotificationDto(
            String notificationId,
            String participantId,
            String severity,
            String rule,
            String title,
            String message,
            String state,
            long occurredAtMs) {
    }
}
