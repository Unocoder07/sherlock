import { stateStyle } from "@/lib/types/state-style";

/** Colored state pill. Color + icon + label together — never color alone. */
export function StateBadge({ state }: { state: string }) {
  const s = stateStyle(state);
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-medium"
      style={{ color: s.color, border: `1px solid ${s.color}`, background: "transparent" }}
    >
      <span aria-hidden>{s.icon}</span>
      {s.label}
    </span>
  );
}
