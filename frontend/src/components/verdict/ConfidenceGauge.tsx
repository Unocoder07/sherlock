/**
 * Semicircular meter for a single magnitude (confidence 0..1) with the score as a
 * hero number. The arc fill takes the state's reserved status color so the meter
 * reinforces the badge beside it; the track is a recessive hairline tone.
 */
export function ConfidenceGauge({ score, color }: { score: number; color: string }) {
  const clamped = Math.max(0, Math.min(1, score));
  const r = 70;
  const cx = 90;
  const cy = 90;
  const circumference = Math.PI * r; // half circle
  const dash = circumference * clamped;

  // Semicircle path from left (180°) to right (0°).
  const arc = `M ${cx - r} ${cy} A ${r} ${r} 0 0 1 ${cx + r} ${cy}`;

  return (
    <div className="flex flex-col items-center">
      <svg width={180} height={104} viewBox="0 0 180 104" role="img" aria-label={`Confidence ${(clamped * 100).toFixed(0)} percent`}>
        <path d={arc} fill="none" stroke="var(--hairline)" strokeWidth={14} strokeLinecap="round" />
        <path
          d={arc}
          fill="none"
          stroke={color}
          strokeWidth={14}
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circumference}`}
          style={{ transition: "stroke-dasharray 400ms ease, stroke 400ms ease" }}
        />
      </svg>
      <div className="-mt-8 text-center">
        <div className="text-4xl font-semibold" style={{ color: "var(--text-primary)" }}>
          {(clamped * 100).toFixed(0)}
          <span className="text-xl" style={{ color: "var(--text-muted)" }}>%</span>
        </div>
        <div className="text-xs uppercase tracking-wide" style={{ color: "var(--text-muted)" }}>
          confidence
        </div>
      </div>
    </div>
  );
}
