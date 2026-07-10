package com.sherlock.edgegateway.application;

import com.sherlock.contracts.notification.v1.Notification;
import com.sherlock.contracts.notification.v1.Severity;
import com.sherlock.edgegateway.dto.AlertMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fans alerts out to a meeting's alert topic and remembers the latest alert per
 * meeting so a dashboard connecting mid-interview immediately sees an active
 * warning on subscribe (mirrors the verdict snapshot, doc 08 §4).
 */
@Service
public class AlertBroadcastService {

    private final SimpMessagingTemplate messaging;

    /** meetingId -> latest alert seen. */
    private final Map<String, AlertMessage> latest = new ConcurrentHashMap<>();

    public AlertBroadcastService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** Map, cache, and broadcast a new alert to the meeting's subscribers. */
    public void broadcast(Notification notification) {
        AlertMessage msg = toMessage(notification);
        latest.put(msg.meetingId(), msg);
        messaging.convertAndSend(topic(msg.meetingId()), msg);
    }

    /** Latest alert for a meeting (empty if none seen yet). */
    public List<AlertMessage> snapshot(String meetingId) {
        AlertMessage msg = latest.get(meetingId);
        return msg == null ? List.of() : List.of(msg);
    }

    /** STOMP destination a dashboard subscribes to for a meeting's alerts. */
    public static String topic(String meetingId) {
        return "/topic/meetings/" + meetingId + "/alert";
    }

    static AlertMessage toMessage(Notification n) {
        return new AlertMessage(
                n.getMeetingId(),
                n.getParticipantId(),
                n.getNotificationId(),
                cleanSeverity(n.getSeverity()),
                n.getRule(),
                n.getTitle(),
                n.getMessage(),
                n.getState(),
                n.getOccurredAtMs());
    }

    /** "SEVERITY_CRITICAL" -> "CRITICAL". */
    private static String cleanSeverity(Severity severity) {
        String name = severity.name();
        String prefix = "SEVERITY_";
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }
}
