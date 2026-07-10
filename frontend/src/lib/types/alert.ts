// Mirror of the edge-gateway AlertMessage DTO. Keep in sync with
// backend/services/edge-gateway/.../dto/AlertMessage.java.

export type Severity = "INFO" | "WARNING" | "CRITICAL" | string;

export interface AlertMessage {
  meetingId: string;
  participantId: string;
  notificationId: string;
  severity: Severity;
  rule: string;
  title: string;
  message: string;
  state: string;
  occurredAtMs: number;
}
