"use client";

import Link from "next/link";
import { useVerdictStream } from "@/hooks/useVerdictStream";
import { useTimelineStream } from "@/hooks/useTimelineStream";
import { useAlertStream } from "@/hooks/useAlertStream";
import { VerdictPanel } from "@/components/verdict/VerdictPanel";
import { TimelinePanel } from "@/components/timeline/TimelinePanel";
import { AlertBanner } from "@/components/alerts/AlertBanner";
import type { ConnectionStatus } from "@/lib/types/verdict";

const CONN: Record<ConnectionStatus, { color: string; label: string }> = {
  connecting: { color: "var(--status-warning)", label: "Connecting" },
  connected: { color: "var(--status-good)", label: "Live" },
  disconnected: { color: "var(--status-critical)", label: "Disconnected" },
};

export default function MeetingDashboard({ params }: { params: { id: string } }) {
  const meetingId = decodeURIComponent(params.id);
  const { status, verdicts } = useVerdictStream(meetingId);
  const { entries } = useTimelineStream(meetingId);
  const { latestCritical } = useAlertStream(meetingId);
  const conn = CONN[status];

  return (
    <main className="mx-auto max-w-6xl px-6 py-8">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <Link href="/" className="text-sm" style={{ color: "var(--text-muted)" }}>
            ← meetings
          </Link>
          <h1 className="mt-1 text-xl font-semibold">Live verdict</h1>
          <p className="text-xs tabular-nums" style={{ color: "var(--text-muted)" }}>
            {meetingId}
          </p>
        </div>
        <span className="inline-flex items-center gap-2 text-sm" style={{ color: conn.color }}>
          <span
            className="h-2.5 w-2.5 rounded-full"
            style={{ background: conn.color, boxShadow: `0 0 8px ${conn.color}` }}
            aria-hidden
          />
          {conn.label}
        </span>
      </header>

      <AlertBanner alert={latestCritical} />

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {verdicts.length === 0 ? (
            <div className="card p-10 text-center" style={{ color: "var(--text-secondary)" }}>
              <p className="text-sm">
                Waiting for signals. Drive this meeting with the signal simulator to see the
                verdict form.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
              {verdicts.map((v) => (
                <VerdictPanel key={v.participantId} verdict={v} />
              ))}
            </div>
          )}
        </div>

        <div className="lg:col-span-1">
          <TimelinePanel entries={entries} />
        </div>
      </div>
    </main>
  );
}
