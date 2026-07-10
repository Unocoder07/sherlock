import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

// sockjs-client references the Node-style `global`; alias it to window in the
// browser bundle so SockJS can instantiate under Next.js.
if (typeof window !== "undefined" && typeof (window as unknown as { global?: unknown }).global === "undefined") {
  (window as unknown as { global: unknown }).global = window;
}

/**
 * The edge-gateway STOMP endpoint. Browser-side value, so it must be the HOST URL
 * (localhost:8094), not the in-docker service name. Overridable at build/runtime
 * via NEXT_PUBLIC_GATEWAY_WS_URL.
 */
export function gatewayWsUrl(): string {
  return process.env.NEXT_PUBLIC_GATEWAY_WS_URL ?? "http://localhost:8094/ws";
}

/**
 * Build a STOMP-over-SockJS client with auto-reconnect. Callers set
 * onConnect/onWebSocketClose and activate() it. SockJS gives us a transport
 * fallback and matches the gateway's `.withSockJS()` endpoint.
 */
export function createStompClient(): Client {
  return new Client({
    webSocketFactory: () => new SockJS(gatewayWsUrl()),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });
}
