// Headless STOMP-over-SockJS client to prove the M4 fan-out end to end: it
// subscribes to a meeting's verdict, timeline, and alert topics on the edge
// gateway, collects frames for N seconds, then prints a summary + PASS/FAIL
// assertions. Drive the meeting in parallel with the signal-simulator, e.g.:
//
//   node m4-e2e.js <meetingId> 40 &
//   (cd ../backend && ./gradlew :tools:signal-simulator:run \
//        --args="--scenario proxy --meeting <meetingId>")
//
// Usage: node m4-e2e.js <meetingId> [seconds] [expect]
//   expect = "identified" | "proxy"  (optional; tunes the PASS/FAIL check)
const { Client } = require("@stomp/stompjs");
const SockJS = require("sockjs-client");

const meetingId = process.argv[2];
const seconds = Number(process.argv[3] || 45);
const expect = (process.argv[4] || "").toLowerCase();
if (!meetingId) {
  console.error("usage: node m4-e2e.js <meetingId> [seconds] [identified|proxy]");
  process.exit(2);
}

const verdicts = [];
const timeline = [];
const alerts = [];
const stateTrajectory = [];

const client = new Client({
  webSocketFactory: () => new SockJS("http://localhost:8094/ws"),
  reconnectDelay: 2000,
});

client.onConnect = () => {
  console.log("CONNECTED, subscribing (verdict/timeline/alert) to", meetingId);

  client.subscribe(`/topic/meetings/${meetingId}/verdict`, (m) => {
    const v = JSON.parse(m.body);
    verdicts.push(v);
    if (stateTrajectory[stateTrajectory.length - 1] !== v.state) stateTrajectory.push(v.state);
    const reasonText = (v.reasons[0] && v.reasons[0].text) || "(none)";
    console.log(
      `VERDICT #${verdicts.length} state=${v.state} score=${v.score.toFixed(3)} ` +
        `headline="${v.headline}" topReason="${reasonText}"`,
    );
  });

  client.subscribe(`/topic/meetings/${meetingId}/timeline`, (m) => {
    const e = JSON.parse(m.body);
    timeline.push(e);
    console.log(
      `TIMELINE #${timeline.length} kind=${e.kind} ${e.fromState}->${e.toState} ` +
        `headline="${e.headline}" detail="${e.detail}"`,
    );
  });

  client.subscribe(`/topic/meetings/${meetingId}/alert`, (m) => {
    const a = JSON.parse(m.body);
    alerts.push(a);
    console.log(`ALERT #${alerts.length} severity=${a.severity} title="${a.title}" rule=${a.rule}`);
  });
};
client.onStompError = (f) => console.log("STOMP ERROR", f.headers && f.headers.message);
client.onWebSocketClose = () => console.log("WS CLOSED");
client.activate();

setTimeout(() => {
  console.log("──────────────────────────────────────────────");
  console.log(`verdicts=${verdicts.length} timeline=${timeline.length} alerts=${alerts.length}`);
  console.log(`stateTrajectory=${stateTrajectory.join(" -> ")}`);

  const englishReasons = verdicts.some((v) => v.reasons.some((r) => r.text && r.text.length > 0));
  const hasTransitions = timeline.some((e) => e.kind === "STATE_TRANSITION");
  const criticalAlert = alerts.some((a) => a.severity === "CRITICAL");
  const reachedIdentified = stateTrajectory.includes("IDENTIFIED");
  const reachedProxy = stateTrajectory.includes("PROXY_SUSPECTED");

  const checks = [
    ["received verdicts", verdicts.length > 0],
    ["reasons render in English", englishReasons],
    ["timeline has state transitions", hasTransitions],
  ];
  if (expect === "identified") checks.push(["reached IDENTIFIED", reachedIdentified]);
  if (expect === "proxy") {
    checks.push(["reached PROXY_SUSPECTED", reachedProxy]);
    checks.push(["raised a CRITICAL alert", criticalAlert]);
  }

  console.log("──────────────────────────────────────────────");
  let ok = true;
  for (const [label, pass] of checks) {
    console.log(`${pass ? "PASS" : "FAIL"}  ${label}`);
    ok = ok && pass;
  }
  console.log(ok ? "RESULT: PASS" : "RESULT: FAIL");
  client.deactivate().finally(() => process.exit(ok ? 0 : 1));
}, seconds * 1000);
