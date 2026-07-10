import type { VerdictMessage } from "@/lib/types/verdict";
import { stateStyle } from "@/lib/types/state-style";
import { ConfidenceGauge } from "./ConfidenceGauge";
import { ReasonList } from "./ReasonList";
import { StateBadge } from "@/components/participants/StateBadge";

/** One candidate's verdict: identity + state badge + confidence meter + reasons. */
export function VerdictPanel({ verdict }: { verdict: VerdictMessage }) {
  const s = stateStyle(verdict.state);
  return (
    <div className="card p-6">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <div className="text-xs uppercase tracking-wide" style={{ color: "var(--text-muted)" }}>
            candidate
          </div>
          <div className="text-lg font-medium" style={{ color: "var(--text-primary)" }}>
            {verdict.participantId}
          </div>
        </div>
        <StateBadge state={verdict.state} />
      </div>

      <div className="flex flex-col items-center gap-1 py-2">
        <ConfidenceGauge score={verdict.score} color={s.color} />
        {verdict.separation > 0 && (
          <div className="text-xs" style={{ color: "var(--text-muted)" }}>
            separation {verdict.separation.toFixed(2)}
          </div>
        )}
      </div>

      {verdict.headline && (
        <p className="mt-1 text-center text-sm" style={{ color: s.color }}>
          {verdict.headline}
        </p>
      )}

      <div className="mt-4">
        <div className="mb-2 text-xs uppercase tracking-wide" style={{ color: "var(--text-muted)" }}>
          why
        </div>
        <ReasonList reasons={verdict.reasons} />
      </div>
    </div>
  );
}
