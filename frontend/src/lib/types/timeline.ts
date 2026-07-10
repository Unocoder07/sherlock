// Mirror of the edge-gateway TimelineMessage DTO. Keep in sync with
// backend/services/edge-gateway/.../dto/TimelineMessage.java.

export interface TimelineEntry {
  meetingId: string;
  participantId: string;
  entryId: string;
  kind: "STATE_TRANSITION" | "SCORE_INFLECTION" | string;
  fromState: string;
  toState: string;
  score: number; // 0..1
  headline: string;
  detail: string;
  occurredAtMs: number;
}
