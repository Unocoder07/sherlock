"use client";

import { useEffect, useState } from "react";
import type { IMessage } from "@stomp/stompjs";
import { createStompClient } from "@/lib/ws/client";
import type { TimelineEntry } from "@/lib/types/timeline";

/**
 * Subscribe to a meeting's timeline stream. Connects to the edge-gateway and
 * subscribes to /topic/meetings/{id}/timeline (the gateway replays recent entries
 * on subscribe, then pushes live ones). Returns entries newest-first, deduped by
 * entryId so a subscribe-time replay can't double up with a live push.
 */
export function useTimelineStream(meetingId: string) {
  const [entries, setEntries] = useState<TimelineEntry[]>([]);

  useEffect(() => {
    if (!meetingId) return;
    setEntries([]);

    const client = createStompClient();
    const seen = new Set<string>();

    client.onConnect = () => {
      client.subscribe(`/topic/meetings/${meetingId}/timeline`, (frame: IMessage) => {
        try {
          const entry = JSON.parse(frame.body) as TimelineEntry;
          if (seen.has(entry.entryId)) return;
          seen.add(entry.entryId);
          setEntries((prev) =>
            [entry, ...prev].sort((a, b) => b.occurredAtMs - a.occurredAtMs),
          );
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

  return { entries };
}
