package com.sherlock.edgegateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket wiring (doc 08 §4). Browsers connect at {@code /ws} (SockJS
 * fallback enabled) and SUBSCRIBE to {@code /topic/meetings/{id}/verdict}. A simple
 * in-memory broker is sufficient for M3 — a single gateway instance fanning out one
 * topic family; a real broker relay (RabbitMQ/Redis) is a scale-out concern for later.
 *
 * <p>CORS: the handshake origin is restricted to the configured frontend origin(s);
 * M3 adds no auth beyond that (JWT on CONNECT/SUBSCRIBE is deferred).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;

    public WebSocketConfig(@Value("${sherlock.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}
