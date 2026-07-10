package com.sherlock.edgegateway.adapter.in.ws;

import com.sherlock.edgegateway.application.AlertBroadcastService;
import com.sherlock.edgegateway.application.TimelineBroadcastService;
import com.sherlock.edgegateway.application.VerdictBroadcastService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On SUBSCRIBE to a meeting's verdict / timeline / alert topic, immediately replay
 * the current snapshot to just that session (doc 08 §4) so a dashboard paints
 * without waiting for the next Kafka event: the latest verdict(s), the recent
 * timeline entries, and any active alert. If nothing has been seen for the meeting
 * yet, nothing is sent and the UI shows its "waiting" state.
 *
 * <p>Targeting a single session: the SimpleBroker honours a {@code sessionId} on the
 * message headers and delivers only to that session, even for a shared /topic
 * destination.
 */
@Component
public class SubscriptionSnapshotListener {

    private static final Pattern VERDICT_DEST = Pattern.compile("^/topic/meetings/([^/]+)/verdict$");
    private static final Pattern TIMELINE_DEST = Pattern.compile("^/topic/meetings/([^/]+)/timeline$");
    private static final Pattern ALERT_DEST = Pattern.compile("^/topic/meetings/([^/]+)/alert$");

    private final VerdictBroadcastService verdicts;
    private final TimelineBroadcastService timelines;
    private final AlertBroadcastService alerts;
    private final SimpMessagingTemplate messaging;

    public SubscriptionSnapshotListener(VerdictBroadcastService verdicts,
                                        TimelineBroadcastService timelines,
                                        AlertBroadcastService alerts,
                                        SimpMessagingTemplate messaging) {
        this.verdicts = verdicts;
        this.timelines = timelines;
        this.alerts = alerts;
        this.messaging = messaging;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        if (destination == null || sessionId == null) {
            return;
        }

        Matcher verdict = VERDICT_DEST.matcher(destination);
        if (verdict.matches()) {
            replay(destination, sessionId, verdicts.snapshot(verdict.group(1)));
            return;
        }
        Matcher timeline = TIMELINE_DEST.matcher(destination);
        if (timeline.matches()) {
            replay(destination, sessionId, timelines.snapshot(timeline.group(1)));
            return;
        }
        Matcher alert = ALERT_DEST.matcher(destination);
        if (alert.matches()) {
            replay(destination, sessionId, alerts.snapshot(alert.group(1)));
        }
    }

    private void replay(String destination, String sessionId, List<?> snapshot) {
        for (Object msg : snapshot) {
            messaging.convertAndSend(destination, msg, sessionHeaders(sessionId));
        }
    }

    private static Map<String, Object> sessionHeaders(String sessionId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setLeaveMutable(true);
        return headers.getMessageHeaders();
    }
}
