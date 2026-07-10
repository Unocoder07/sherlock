package com.sherlock.edgegateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Edge Gateway (M3). The browser-facing edge: it consumes {@code confidence.updates}
 * off Kafka and fans each verdict out over STOMP-over-WebSocket to every dashboard
 * subscribed to that meeting. It holds no business logic — it routes and shapes.
 *
 * <p>M3 scope is CORS-only (no auth); JWT/RBAC/rate-limiting from doc 08 land later.
 */
@SpringBootApplication
public class EdgeGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdgeGatewayApplication.class, args);
    }
}
