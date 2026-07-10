package com.sherlock.meeting;

import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.meeting.v1.MeetingCreated;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full-slice integration test: REST -> use case -> Postgres -> outbox -> relay -> Kafka.
 * Proves the M1 exit criterion end-to-end against REAL Postgres + Kafka (Testcontainers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MeetingFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("sherlock");

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("sherlock.outbox.poll-interval-ms", () -> "200");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void createMeeting_persists_andPublishesMeetingCreatedToKafka() {
        // when: create a meeting via REST
        ResponseEntity<Map> created = rest.postForEntity(
                url("/api/v1/meetings"),
                Map.of("title", "Backend interview", "externalRef", "ext-42"),
                Map.class);

        // then: 201 + body
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String meetingId = (String) created.getBody().get("id");
        assertThat(meetingId).isNotBlank();
        assertThat(created.getBody().get("state")).isEqualTo("SCHEDULED");

        // and: the outbox relay publishes a MeetingCreated envelope to meeting.events
        try (KafkaConsumer<String, byte[]> consumer = consumer()) {
            consumer.subscribe(List.of("meeting.events"));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var records = consumer.poll(Duration.ofMillis(500));
                boolean found = false;
                for (ConsumerRecord<String, byte[]> rec : records) {
                    EventEnvelope env = EventEnvelope.parseFrom(rec.value());
                    if (env.getEventType().equals("sherlock.meeting.v1.MeetingCreated")) {
                        assertThat(rec.key()).isEqualTo(meetingId);          // partition key = meetingId
                        assertThat(env.getMeetingId()).isEqualTo(meetingId);
                        MeetingCreated payload = MeetingCreated.parseFrom(env.getPayload());
                        assertThat(payload.getTitle()).isEqualTo("Backend interview");
                        found = true;
                    }
                }
                assertThat(found).isTrue();
            });
        }
    }

    private KafkaConsumer<String, byte[]> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
