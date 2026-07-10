// Maps a ParticipantState label to a reserved status color + an icon glyph.
// Status color never carries meaning alone — every consumer renders the label
// text and this icon alongside the color (data-viz status rule).

export interface StateStyle {
  color: string; // css var reference
  icon: string; // short glyph paired with the label
  label: string;
}

const MUTED = "var(--text-muted)";

export function stateStyle(state: string): StateStyle {
  switch (state) {
    case "IDENTIFIED":
      return { color: "var(--status-good)", icon: "✓", label: "Identified" };
    case "OBSERVING":
      return { color: "var(--status-warning)", icon: "…", label: "Observing" };
    case "ANCHORING":
      return { color: "var(--status-warning)", icon: "◐", label: "Anchoring" };
    case "UNCERTAIN":
      return { color: "var(--status-warning)", icon: "?", label: "Uncertain" };
    case "PROXY_SUSPECTED":
      return { color: "var(--status-critical)", icon: "!", label: "Proxy suspected" };
    case "CANDIDATE_SWITCHED":
      return { color: "var(--status-critical)", icon: "⇄", label: "Candidate switched" };
    case "SIGNAL_LOST":
      return { color: "var(--status-serious)", icon: "⚠", label: "Signal lost" };
    case "LEFT":
      return { color: MUTED, icon: "→", label: "Left" };
    default:
      return { color: MUTED, icon: "·", label: "Unspecified" };
  }
}
