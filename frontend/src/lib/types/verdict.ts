// Mirror of the edge-gateway VerdictMessage DTO (hand-written for the MVP;
// contracts -> TS codegen is a later refinement). Keep in sync with
// backend/services/edge-gateway/.../dto/VerdictMessage.java. As of M4 the reasons
// are rendered to English by the Explanation Engine, so each carries `text`.

export interface Reason {
  text: string; // rendered English sentence, e.g. "✓ Voice stayed dominant"
  evidenceType: string;
  polarity: number; // +1 corroborating, -1 contradicting
  magnitude: number; // |value| * weight — used for bar length / emphasis
}

export interface VerdictMessage {
  meetingId: string;
  participantId: string;
  score: number; // 0..1
  state: string; // clean label, e.g. "IDENTIFIED"
  previousState: string;
  separation: number;
  headline: string; // one-line summary, e.g. "Candidate identified"
  reasons: Reason[];
  occurredAtMs: number;
}

export type ConnectionStatus = "connecting" | "connected" | "disconnected";
