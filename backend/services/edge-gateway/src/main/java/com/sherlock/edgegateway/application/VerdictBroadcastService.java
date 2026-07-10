package com.sherlock.edgegateway.application;

import com.sherlock.contracts.explanation.v1.EnrichedVerdict;
import com.sherlock.contracts.explanation.v1.RenderedReason;
import com.sherlock.edgegateway.dto.VerdictMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns an {@link EnrichedVerdict} (verdict + English reasons, produced by the
 * Explanation Engine) into a browser-facing {@link VerdictMessage}, remembers the
 * last-known verdict per (meeting, participant), and broadcasts it to everyone
 * subscribed to that meeting's topic.
 *
 * <p>The snapshot cache backs the "paint immediately on subscribe" behaviour
 * (doc 08 §4): a dashboard that connects mid-interview gets the current verdict
 * without waiting for the next Kafka event. An in-memory map is sufficient for a
 * single gateway instance; a Redis-backed snapshot is a scale-out concern for later.
 */
@Service
public class VerdictBroadcastService {

    private final SimpMessagingTemplate messaging;

    /** meetingId -> participantId -> latest verdict. */
    private final Map<String, Map<String, VerdictMessage>> snapshots = new ConcurrentHashMap<>();

    public VerdictBroadcastService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** Map, cache, and broadcast a fresh update to all subscribers of its meeting. */
    public void broadcast(EnrichedVerdict update) {
        VerdictMessage msg = toMessage(update);
        snapshots
                .computeIfAbsent(msg.meetingId(), k -> new ConcurrentHashMap<>())
                .put(msg.participantId(), msg);
        messaging.convertAndSend(topic(msg.meetingId()), msg);
    }

    /** Last-known verdicts for a meeting (empty if none seen yet). */
    public List<VerdictMessage> snapshot(String meetingId) {
        Map<String, VerdictMessage> byParticipant = snapshots.get(meetingId);
        return byParticipant == null ? List.of() : List.copyOf(byParticipant.values());
    }

    /** STOMP destination a dashboard subscribes to for a meeting's verdicts. */
    public static String topic(String meetingId) {
        return "/topic/meetings/" + meetingId + "/verdict";
    }

    static VerdictMessage toMessage(EnrichedVerdict u) {
        List<VerdictMessage.Reason> reasons = u.getReasonsList().stream()
                .map(VerdictBroadcastService::toReason)
                .toList();
        return new VerdictMessage(
                u.getMeetingId(),
                u.getParticipantId(),
                u.getScore(),
                u.getState(),            // already clean (stripped by the Explanation Engine)
                u.getPreviousState(),
                u.getSeparation(),
                u.getHeadline(),
                reasons,
                u.getOccurredAtMs());
    }

    private static VerdictMessage.Reason toReason(RenderedReason r) {
        return new VerdictMessage.Reason(r.getText(), r.getEvidenceType(), r.getPolarity(), r.getMagnitude());
    }
}
