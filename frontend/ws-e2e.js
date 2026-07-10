// Headless STOMP-over-SockJS client to prove the edge-gateway fan-out end to end.
// Usage: node ws-e2e.js <meetingId> [seconds]
const { Client } = require("@stomp/stompjs");
const SockJS = require("sockjs-client");

const meetingId = process.argv[2];
const seconds = Number(process.argv[3] || 70);
if (!meetingId) {
  console.error("usage: node ws-e2e.js <meetingId> [seconds]");
  process.exit(2);
}

let count = 0;
const states = [];
const client = new Client({
  webSocketFactory: () => new SockJS("http://localhost:8094/ws"),
  reconnectDelay: 2000,
});

client.onConnect = () => {
  console.log("CONNECTED, subscribing to meeting", meetingId);
  client.subscribe(`/topic/meetings/${meetingId}/verdict`, (m) => {
    const v = JSON.parse(m.body);
    count++;
    const last = states[states.length - 1];
    if (v.state !== last) states.push(v.state);
    console.log(
      `VERDICT #${count} participant=${v.participantId} state=${v.state} ` +
        `score=${v.score.toFixed(3)} reasons=${v.reasons.length}`,
    );
  });
};
client.onStompError = (f) => console.log("STOMP ERROR", f.headers && f.headers.message);
client.onWebSocketClose = () => console.log("WS CLOSED");
client.activate();

setTimeout(() => {
  console.log(`DONE total=${count} stateTrajectory=${states.join(" -> ")}`);
  client.deactivate().finally(() => process.exit(0));
}, seconds * 1000);
