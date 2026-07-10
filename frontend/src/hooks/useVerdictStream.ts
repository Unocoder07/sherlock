"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { IMessage } from "@stomp/stompjs";
import { createStompClient } from "@/lib/ws/client";
import type { ConnectionStatus, VerdictMessage } from "@/lib/types/verdict";

/**
 * Subscribe to a meeting's verdict stream. Connects to the edge-gateway, subscribes
 * to /topic/meetings/{id}/verdict, and keeps the latest VerdictMessage per
 * participant (the gateway replays a snapshot on subscribe, then pushes live
 * updates). Returns the verdicts (highest score first) and the connection status.
 */
export function useVerdictStream(meetingId: string) {
  const [status, setStatus] = useState<ConnectionStatus>("connecting");
  const [byParticipant, setByParticipant] = useState<Record<string, VerdictMessage>>({});
  const seenRef = useRef(false);

  useEffect(() => {
    if (!meetingId) return;
    setStatus("connecting");
    setByParticipant({});

    const client = createStompClient();

    client.onConnect = () => {
      setStatus("connected");
      client.subscribe(`/topic/meetings/${meetingId}/verdict`, (frame: IMessage) => {
        try {
          const msg = JSON.parse(frame.body) as VerdictMessage;
          seenRef.current = true;
          setByParticipant((prev) => ({ ...prev, [msg.participantId]: msg }));
        } catch {
          /* ignore malformed frame */
        }
      });
    };
    client.onWebSocketClose = () => setStatus("disconnected");
    client.onStompError = () => setStatus("disconnected");

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [meetingId]);

  const verdicts = useMemo(
    () => Object.values(byParticipant).sort((a, b) => b.score - a.score),
    [byParticipant],
  );

  return { status, verdicts, hasData: seenRef.current };
}
