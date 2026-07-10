import type { AlertMessage } from "@/lib/types/alert";

// A pinned banner for the most serious active alert. Colored by severity, but the
// severity word + title text always carry the meaning (never color alone).
const SEVERITY: Record<string, { color: string; icon: string }> = {
  CRITICAL: { color: "var(--status-critical)", icon: "!" },
  WARNING: { color: "var(--status-serious)", icon: "⚠" },
  INFO: { color: "var(--status-good)", icon: "✓" },
};

export function AlertBanner({ alert }: { alert: AlertMessage | null }) {
  if (!alert) return null;
  const s = SEVERITY[alert.severity] ?? { color: "var(--text-muted)", icon: "·" };

  return (
    <div
      role="alert"
      className="mb-5 flex items-start gap-3 rounded-lg px-4 py-3"
      style={{ background: "color-mix(in srgb, var(--status-critical) 12%, transparent)", border: `1px solid ${s.color}` }}
    >
      <span
        className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-sm font-bold"
        style={{ background: s.color, color: "#fff" }}
        aria-hidden
      >
        {s.icon}
      </span>
      <div className="min-w-0">
        <div className="flex items-center gap-2 text-sm font-semibold" style={{ color: s.color }}>
          <span>{alert.severity}</span>
          <span style={{ color: "var(--text-primary)" }}>{alert.title}</span>
        </div>
        <p className="mt-0.5 text-sm" style={{ color: "var(--text-secondary)" }}>
          {alert.message}
        </p>
      </div>
    </div>
  );
}
