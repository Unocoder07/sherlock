"use client";

import { useEffect, useState } from "react";
import type { IMessage } from "@stomp/stompjs";
import { createStompClient } from "@/lib/ws/client";
import type { AlertMessage } from "@/lib/types/alert";

/**
 * Subscribe to a meeting's alert stream. Connects to the edge-gateway and
 * subscribes to /topic/meetings/{id}/alert (the gateway replays the active alert
 * on subscribe, then pushes live ones). Returns the latest alert and the latest
 * CRITICAL alert separately, so the banner can stay pinned on a critical even if a
 * later INFO arrives.
 */
export function useAlertStream(meetingId: string) {
  const [latest, setLatest] = useState<AlertMessage | null>(null);
  const [latestCritical, setLatestCritical] = useState<AlertMessage | null>(null);

  useEffect(() => {
    if (!meetingId) return;
    setLatest(null);
    setLatestCritical(null);

    const client = createStompClient();

    client.onConnect = () => {
      client.subscribe(`/topic/meetings/${meetingId}/alert`, (frame: IMessage) => {
        try {
          const alert = JSON.parse(frame.body) as AlertMessage;
          setLatest(alert);
          if (alert.severity === "CRITICAL") setLatestCritical(alert);
        } catch {
          /* ignore malformed frame */
        }
      });
    };

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [meetingId]);

  return { latest, latestCritical };
}
