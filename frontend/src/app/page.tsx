"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

/**
 * Landing page. There is no list-meetings API in M3, so the operator pastes the
 * meeting id they are driving with the signal-simulator and jumps to its live
 * dashboard.
 */
export default function Home() {
  const router = useRouter();
  const [id, setId] = useState("");

  function go(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = id.trim();
    if (trimmed) router.push(`/meetings/${encodeURIComponent(trimmed)}`);
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center px-6">
      <h1 className="text-2xl font-semibold">Sherlock</h1>
      <p className="mt-1 mb-6 text-sm" style={{ color: "var(--text-secondary)" }}>
        Live interview identity verdict. Enter the meeting id you are driving with the
        signal simulator.
      </p>
      <form onSubmit={go} className="flex gap-2">
        <input
          value={id}
          onChange={(e) => setId(e.target.value)}
          placeholder="meeting id (uuid)"
          className="flex-1 rounded-lg px-3 py-2 text-sm outline-none"
          style={{ background: "var(--surface-1)", border: "1px solid var(--border-ring)", color: "var(--text-primary)" }}
        />
        <button
          type="submit"
          className="rounded-lg px-4 py-2 text-sm font-medium"
          style={{ background: "var(--series-blue)", color: "#fff" }}
        >
          Watch
        </button>
      </form>
    </main>
  );
}
