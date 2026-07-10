import type { TimelineEntry } from "@/lib/types/timeline";
import { stateStyle } from "@/lib/types/state-style";

function formatTime(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

/** A live, newest-first history of state transitions and score inflections. */
export function TimelinePanel({ entries }: { entries: TimelineEntry[] }) {
  return (
    <div className="card p-6">
      <div className="mb-3 text-xs uppercase tracking-wide" style={{ color: "var(--text-muted)" }}>
        timeline
      </div>

      {entries.length === 0 ? (
        <p className="text-sm" style={{ color: "var(--text-muted)" }}>
          No events yet.
        </p>
      ) : (
        <ol className="flex flex-col gap-3">
          {entries.map((e) => {
            const s = stateStyle(e.toState);
            const isTransition = e.kind === "STATE_TRANSITION";
            return (
              <li key={e.entryId} className="flex gap-3 text-sm">
                <span
                  className="mt-1 h-2 w-2 shrink-0 rounded-full"
                  style={{ background: s.color, boxShadow: `0 0 6px ${s.color}` }}
                  aria-hidden
                />
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-3">
                    <span style={{ color: "var(--text-primary)" }}>
                      {isTransition ? (
                        <>
                          {e.fromState ? (
                            <span style={{ color: "var(--text-muted)" }}>{e.fromState} → </span>
                          ) : null}
                          <span style={{ color: s.color }}>{s.label}</span>
                        </>
                      ) : (
                        e.headline
                      )}
                    </span>
                    <span className="shrink-0 tabular-nums text-xs" style={{ color: "var(--text-muted)" }}>
                      {formatTime(e.occurredAtMs)}
                    </span>
                  </div>
                  {e.detail && (
                    <p className="mt-0.5" style={{ color: "var(--text-secondary)" }}>
                      {e.detail}
                    </p>
                  )}
                  <p className="mt-0.5 text-xs tabular-nums" style={{ color: "var(--text-muted)" }}>
                    {e.participantId} · score {e.score.toFixed(2)}
                  </p>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
