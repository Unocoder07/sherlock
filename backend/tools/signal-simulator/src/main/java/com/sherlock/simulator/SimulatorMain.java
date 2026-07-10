package com.sherlock.simulator;

import com.google.protobuf.Message;
import com.sherlock.common.kafka.EnvelopeCodec;
import com.sherlock.contracts.common.v1.EventEnvelope;
import com.sherlock.contracts.meeting.v1.ParticipantJoined;
import com.sherlock.contracts.signals.v1.AudioSignal;
import com.sherlock.contracts.signals.v1.AudioSignalType;
import com.sherlock.contracts.signals.v1.VideoSignal;
import com.sherlock.contracts.signals.v1.VideoSignalType;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Map;
import java.util.UUID;

/**
 * Replays a scripted scenario to Kafka to drive the M2 data plane end-to-end
 * (doc 10 M2 exit). Signals are emitted in real time (one window/second) so the
 * Confidence Engine's decay + dwell windows elapse naturally and the verdict
 * trajectory is observable on {@code confidence.updates}.
 *
 *   ./gradlew :tools:signal-simulator:run --args="--scenario cold-start --meeting &lt;uuid&gt;"
 *
 * Scenarios: cold-start (→ IDENTIFIED), proxy &amp; relay (→ PROXY_SUSPECTED),
 * switch (→ CANDIDATE_SWITCHED), camera-off (holds via voice, no false proxy).
 */
public final class SimulatorMain {

    private static final String PRODUCER = "signal-simulator@0.1.0";
    private static final String FACE = "face:john";
    private static final String VOICE = "voice:john";

    private final Producer<String, byte[]> producer;
    private final String meetingId;
    private final String pid;

    private SimulatorMain(Producer<String, byte[]> producer, String meetingId, String pid) {
        this.producer = producer;
        this.meetingId = meetingId;
        this.pid = pid;
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parse(args);
        String scenario = opts.getOrDefault("scenario", "cold-start");
        String meetingId = opts.getOrDefault("meeting", UUID.randomUUID().toString());
        String pid = opts.getOrDefault("participant", "p-john");
        String bootstrap = opts.getOrDefault("bootstrap", "localhost:29092");

        System.out.printf("▶ scenario=%s meeting=%s participant=%s bootstrap=%s%n",
                scenario, meetingId, pid, bootstrap);

        try (Producer<String, byte[]> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true"))) {

            SimulatorMain sim = new SimulatorMain(producer, meetingId, pid);
            sim.join();
            switch (scenario) {
                case "cold-start" -> sim.coldStart();
                case "proxy" -> sim.proxy();
                case "relay" -> sim.relay();
                case "switch" -> sim.candidateSwitch();
                case "camera-off" -> sim.cameraOff();
                default -> throw new IllegalArgumentException("unknown scenario: " + scenario);
            }
            producer.flush();
            System.out.println("✔ scenario complete");
        }
    }

    // ── scenarios ─────────────────────────────────────────────────────────────

    /** Consistent face + voice with lips moving → anchor locks → IDENTIFIED. */
    private void coldStart() throws InterruptedException {
        steadyPositive(18);
    }

    /** Genuine person locks, then an off-screen voice answers (lips silent) → PROXY. */
    private void relay() throws InterruptedException {
        steadyPositive(8);
        for (int i = 0; i < 12; i++) {
            long t = now();
            facePresent(FACE, 0.0, t);                 // face on camera but NOT speaking
            speaking("voice:offscreen", 0.9, t);       // a different voice is active
            sleep();
        }
    }

    /** Off-screen relay from the start (lips never move while a voice answers) → PROXY. */
    private void proxy() throws InterruptedException {
        for (int i = 0; i < 16; i++) {
            long t = now();
            facePresent(FACE, 0.0, t);
            speaking("voice:offscreen", 0.9, t);
            sleep();
        }
    }

    /** Identify, then a different face appears on the stream → CANDIDATE_SWITCHED. */
    private void candidateSwitch() throws InterruptedException {
        steadyPositive(16);
        for (int i = 0; i < 8; i++) {
            long t = now();
            facePresent("face:impostor", 0.8, t);      // embedding drifts from the anchor
            speaking(VOICE, 0.9, t);
            sleep();
        }
    }

    /** Identify, then camera off while voice keeps matching → carried by voice, no false proxy. */
    private void cameraOff() throws InterruptedException {
        steadyPositive(16);
        for (int i = 0; i < 10; i++) {
            long t = now();
            faceAbsent(t);                              // camera off
            speaking(VOICE, 0.9, t);                    // voice still consistent
            sleep();
        }
    }

    /** N seconds of fully-corroborated positive evidence. */
    private void steadyPositive(int seconds) throws InterruptedException {
        for (int i = 0; i < seconds; i++) {
            long t = now();
            facePresent(FACE, 0.8, t);                 // lips moving
            speaking(VOICE, 0.9, t);                    // matching voice
            sleep();
        }
    }

    // ── emit helpers ──────────────────────────────────────────────────────────

    private void join() {
        emit("meeting.events", ParticipantJoined.newBuilder()
                .setMeetingId(meetingId).setParticipantId(pid)
                .setDisplayName("iPhone").setCameraOn(true).build());
    }

    private void facePresent(String token, double lip, long t) {
        emit("video.signals", VideoSignal.newBuilder()
                .setMeetingId(meetingId).setParticipantId(pid)
                .setType(VideoSignalType.VIDEO_SIGNAL_TYPE_FACE_PRESENT)
                .setEmbeddingRef(token).setDetectionConf(0.9f).setQuality(0.9f)
                .setLipActivity((float) lip)
                .setWindowStartMs(t).setWindowEndMs(t).build());
    }

    private void faceAbsent(long t) {
        emit("video.signals", VideoSignal.newBuilder()
                .setMeetingId(meetingId).setParticipantId(pid)
                .setType(VideoSignalType.VIDEO_SIGNAL_TYPE_FACE_ABSENT)
                .setWindowStartMs(t).setWindowEndMs(t).build());
    }

    private void speaking(String token, double dominance, long t) {
        emit("audio.signals", AudioSignal.newBuilder()
                .setMeetingId(meetingId).setParticipantId(pid)
                .setType(AudioSignalType.AUDIO_SIGNAL_TYPE_SPEAKING)
                .setEmbeddingRef(token).setVoiceActive(true)
                .setSpeakingRatio((float) dominance).setDominance((float) dominance).setSnr(0.9f)
                .setWindowStartMs(t).setWindowEndMs(t).build());
    }

    private void emit(String topic, Message payload) {
        long occurredAt = now();
        EventEnvelope env = EnvelopeCodec.wrap(PRODUCER, 1, meetingId, pid, occurredAt, occurredAt, payload);
        producer.send(new ProducerRecord<>(topic, meetingId, env.toByteArray()));
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static void sleep() throws InterruptedException {
        Thread.sleep(1000);
    }

    // ── arg parsing ("--key value") ───────────────────────────────────────────
    private static Map<String, String> parse(String[] args) {
        Map<String, String> out = new java.util.HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (args[i].startsWith("--")) {
                out.put(args[i].substring(2), args[i + 1]);
            }
        }
        return out;
    }
}
