import type { Reason } from "@/lib/types/verdict";

// Diverging encoding: corroborating evidence reads cool (blue), contradicting
// reads warm (red). Bar length is the contribution magnitude, normalized to the
// strongest reason in view. The English text (rendered by the Explanation Engine)
// is the primary label; color sits on the bar only.
const CORROBORATE = "#3987e5";
const CONTRADICT = "#e66767";

export function ReasonList({ reasons }: { reasons: Reason[] }) {
  if (!reasons.length) {
    return <p className="text-sm" style={{ color: "var(--text-muted)" }}>No contributing signals yet.</p>;
  }
  const max = Math.max(...reasons.map((r) => Math.abs(r.magnitude)), 0.0001);
  const sorted = [...reasons].sort((a, b) => Math.abs(b.magnitude) - Math.abs(a.magnitude));

  return (
    <ul className="flex flex-col gap-2.5">
      {sorted.map((r, i) => {
        const pct = (Math.abs(r.magnitude) / max) * 100;
        const positive = r.polarity >= 0;
        return (
          <li key={`${r.evidenceType}-${i}`} className="text-sm">
            <div className="mb-1 flex items-center justify-between gap-3">
              <span style={{ color: "var(--text-secondary)" }}>{r.text}</span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded" style={{ background: "var(--hairline)" }}>
              <div
                className="h-full rounded"
                style={{
                  width: `${pct}%`,
                  background: positive ? CORROBORATE : CONTRADICT,
                  transition: "width 400ms ease",
                }}
              />
            </div>
          </li>
        );
      })}
    </ul>
  );
}
