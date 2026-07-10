package com.sherlock.edgegateway.application;

import com.sherlock.contracts.timeline.v1.EntryKind;
import com.sherlock.contracts.timeline.v1.TimelineEntry;
import com.sherlock.edgegateway.dto.TimelineMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fans timeline entries out to a meeting's timeline topic and keeps a small
 * per-meeting ring buffer so a dashboard connecting mid-interview can replay the
 * recent history on subscribe (mirrors the verdict snapshot, doc 08 §4). The ring
 * is bounded — the full history lives in the Timeline Service's DB / REST query.
 */
@Service
public class TimelineBroadcastService {

    /** How many recent entries to retain per meeting for subscribe-time replay. */
    static final int RING_SIZE = 50;

    private final SimpMessagingTemplate messaging;

    /** meetingId -> bounded deque of recent entries (oldest first). */
    private final Map<String, Deque<TimelineMessage>> recent = new ConcurrentHashMap<>();

    public TimelineBroadcastService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** Map, cache, and broadcast a new timeline entry to the meeting's subscribers. */
    public void broadcast(TimelineEntry entry) {
        TimelineMessage msg = toMessage(entry);
        Deque<TimelineMessage> ring = recent.computeIfAbsent(msg.meetingId(), k -> new ArrayDeque<>());
        synchronized (ring) {
            ring.addLast(msg);
            while (ring.size() > RING_SIZE) {
                ring.removeFirst();
            }
        }
        messaging.convertAndSend(topic(msg.meetingId()), msg);
    }

    /** Recent entries for a meeting, oldest first (empty if none seen yet). */
    public List<TimelineMessage> snapshot(String meetingId) {
        Deque<TimelineMessage> ring = recent.get(meetingId);
        if (ring == null) {
            return List.of();
        }
        synchronized (ring) {
            return List.copyOf(ring);
        }
    }

    /** STOMP destination a dashboard subscribes to for a meeting's timeline. */
    public static String topic(String meetingId) {
        return "/topic/meetings/" + meetingId + "/timeline";
    }

    static TimelineMessage toMessage(TimelineEntry e) {
        return new TimelineMessage(
                e.getMeetingId(),
                e.getParticipantId(),
                e.getEntryId(),
                cleanKind(e.getKind()),
                e.getFromState(),
                e.getToState(),
                e.getScore(),
                e.getHeadline(),
                e.getDetail(),
                e.getOccurredAtMs());
    }

    /** "ENTRY_KIND_STATE_TRANSITION" -> "STATE_TRANSITION". */
    private static String cleanKind(EntryKind kind) {
        String name = kind.name();
        String prefix = "ENTRY_KIND_";
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }
}
