import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Sherlock — Live Verdict",
  description: "Real-time interview identity verdict dashboard",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
