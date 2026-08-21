package io.github.chalsense.core.vector;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.chalsense.core.security.SecurityEvent;
import io.github.chalsense.core.security.SecurityEventSink;
import io.github.chalsense.core.site.CallerContext;
import io.github.chalsense.core.site.SitePolicy;
import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.core.site.SiteRegistry;
import io.github.chalsense.core.site.SiteStatus;
import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.SliderPuzzleGeometry;
import io.github.chalsense.core.state.StoreTicketResult;
import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.core.ticket.ConsumeOutcome;
import io.github.chalsense.core.ticket.ConsumeResult;
import io.github.chalsense.core.ticket.ConsumeTicketCommand;
import io.github.chalsense.core.ticket.TicketConsumer;
import io.github.chalsense.core.verify.ChallengeVerifier;
import io.github.chalsense.core.verify.VerifyChallengeCommand;
import io.github.chalsense.core.verify.VerifyOutcome;
import io.github.chalsense.core.verify.VerifyResult;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ChallengeType;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.Track;
import io.github.chalsense.protocol.TrackEvent;
import io.github.chalsense.protocol.TrackPoint;
import io.github.chalsense.protocol.VerificationTicket;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolVectorTest {
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final JsonMapper STRICT_OBJECT_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @TestFactory
    Stream<DynamicTest> executesVerifyVectors() throws IOException {
        JsonNode root = loadVectors();
        validateVectorHeader(root);
        return stream(root.required("verifyVectors")).map(vector -> DynamicTest.dynamicTest(
                vector.required("id").textValue(),
                () -> executeVerifyVector(root, vector)));
    }

    @TestFactory
    Stream<DynamicTest> executesConsumeVectors() throws IOException {
        JsonNode root = loadVectors();
        return stream(root.required("consumeVectors")).map(vector -> DynamicTest.dynamicTest(
                vector.required("id").textValue(),
                () -> executeConsumeVector(root, vector)));
    }

    @TestFactory
    Stream<DynamicTest> rejectsParserVectorsBeforeStateMutation() throws IOException {
        JsonNode root = loadVectors();
        return stream(root.required("parserVectors")).map(vector -> DynamicTest.dynamicTest(
                vector.required("id").textValue(),
                () -> {
                    assertFalse(referenceParserAccepts(vector.required("rawJson").textValue()));
                    assertEquals("INVALID_REQUEST", vector.required("expected").required("outcome").textValue());
                }));
    }

    @Test
    void matchesApprovedTicketLookupDigest() throws IOException {
        JsonNode ticket = loadVectors().required("fixtures").required("ticket");
        VerificationTicket value = new VerificationTicket(ticket.required("verificationTicket").textValue());
        assertEquals(ticket.required("lookupDigestHex").textValue(), TicketDigest.from(value).hexValue());
        assertEquals("TicketDigest[REDACTED]", TicketDigest.from(value).toString());
    }

    @Test
    void observabilityFailureDoesNotChangeCredentialSemantics() throws IOException {
        JsonNode root = loadVectors();
        SecurityEventSink throwingSink = event -> {
            throw new IllegalStateException("simulated metrics failure");
        };

        TestStateStore verifyStore = new TestStateStore();
        seedChallenge(root, verifyStore);
        long verifyNow = root.required("verifyVectors").get(0).required("now").longValue();
        assertEquals(VerifyOutcome.VERIFICATION_FAILED,
                verifier(root, verifyStore, verifyNow, throwingSink)
                        .verify(verifyCommand(root, root.required("verifyVectors").get(3).required("request"))).outcome());
        assertEquals(1, verifyStore.challengeTakeSuccesses());

        TestStateStore consumeStore = new TestStateStore();
        seedTicket(root, consumeStore);
        JsonNode consumeVector = root.required("consumeVectors").get(1);
        TicketConsumer consumer = new TicketConsumer(
                consumeStore,
                siteRegistry(root),
                fixedClock(consumeVector.required("now").longValue()),
                throwingSink);
        assertEquals(ConsumeOutcome.TICKET_INVALID,
                consumer.consume(consumeCommand(consumeVector.required("request"))).outcome());
        assertEquals(1, consumeStore.ticketTakeSuccesses());
    }

    @Test
    void linearizesConcurrentChallengeVerification() throws Exception {
        JsonNode root = loadVectors();
        JsonNode vector = root.required("concurrencyVectors").get(0);
        TestStateStore store = new TestStateStore();
        seedChallenge(root, store);
        VerifyChallengeCommand command = validVerifyCommand(root);
        ChallengeVerifier verifier = verifier(root, store, root.required("fixtures").required("challenge").required("state")
                .required("issuedAt").longValue() + 1_000, new TestSupport.CollectingSecurityEventSink());

        List<VerifyOutcome> outcomes = runTogether(() -> verifier.verify(command).outcome());

        JsonNode expected = vector.required("expected");
        assertEquals(expected.required("takeSuccessCount").intValue(), store.challengeTakeSuccesses());
        assertEquals(expected.required("ticketIssuedCount").intValue(), store.ticketStoreSuccesses());
        assertEquals(expected.required("outcomes").required("TICKET_ISSUED").intValue(),
                outcomes.stream().filter(outcome -> outcome == VerifyOutcome.TICKET_ISSUED).count());
        assertEquals(expected.required("outcomes").required("CHALLENGE_UNAVAILABLE").intValue(),
                outcomes.stream().filter(outcome -> outcome == VerifyOutcome.CHALLENGE_UNAVAILABLE).count());
    }

    @Test
    void linearizesConcurrentTicketConsumption() throws Exception {
        JsonNode root = loadVectors();
        JsonNode vector = root.required("concurrencyVectors").get(1);
        TestStateStore store = new TestStateStore();
        seedTicket(root, store);
        ConsumeTicketCommand command = consumeCommand(root.required("consumeVectors").get(0).required("request"));
        long now = root.required("consumeVectors").get(0).required("now").longValue();
        TicketConsumer consumer = new TicketConsumer(
                store, siteRegistry(root), fixedClock(now), new TestSupport.CollectingSecurityEventSink());

        List<ConsumeOutcome> outcomes = runTogether(() -> consumer.consume(command).outcome());

        JsonNode expected = vector.required("expected");
        assertEquals(expected.required("takeSuccessCount").intValue(), store.ticketTakeSuccesses());
        assertEquals(expected.required("consumedCount").intValue(),
                outcomes.stream().filter(outcome -> outcome == ConsumeOutcome.CONSUMED).count());
        assertEquals(expected.required("outcomes").required("TICKET_UNAVAILABLE").intValue(),
                outcomes.stream().filter(outcome -> outcome == ConsumeOutcome.TICKET_UNAVAILABLE).count());
    }

    @TestFactory
    Stream<DynamicTest> generatedConcurrentVerificationNeverIssuesMoreThanOneTicket() {
        return Stream.of(2, 3, 8, 16, 64).map(callerCount -> DynamicTest.dynamicTest(
                "verify-callers-" + callerCount,
                () -> {
                    JsonNode root = loadVectors();
                    TestStateStore store = new TestStateStore();
                    seedChallenge(root, store);
                    ChallengeVerifier verifier = verifier(
                            root,
                            store,
                            root.required("verifyVectors").get(0).required("now").longValue(),
                            new TestSupport.CollectingSecurityEventSink());
                    List<VerifyOutcome> outcomes = runConcurrent(
                            callerCount,
                            () -> verifier.verify(validVerifyCommand(root)).outcome());
                    assertEquals(1, outcomes.stream()
                            .filter(outcome -> outcome == VerifyOutcome.TICKET_ISSUED).count());
                    assertEquals(1, store.challengeTakeSuccesses());
                    assertEquals(1, store.ticketStoreSuccesses());
                }));
    }

    @TestFactory
    Stream<DynamicTest> generatedConcurrentConsumptionNeverSucceedsMoreThanOnce() {
        return Stream.of(2, 3, 8, 16, 64).map(callerCount -> DynamicTest.dynamicTest(
                "consume-callers-" + callerCount,
                () -> {
                    JsonNode root = loadVectors();
                    TestStateStore store = new TestStateStore();
                    seedTicket(root, store);
                    JsonNode vector = root.required("consumeVectors").get(0);
                    TicketConsumer consumer = new TicketConsumer(
                            store,
                            siteRegistry(root),
                            fixedClock(vector.required("now").longValue()),
                            new TestSupport.CollectingSecurityEventSink());
                    ConsumeTicketCommand command = consumeCommand(vector.required("request"));
                    List<ConsumeOutcome> outcomes = runConcurrent(
                            callerCount,
                            () -> consumer.consume(command).outcome());
                    assertEquals(1, outcomes.stream()
                            .filter(outcome -> outcome == ConsumeOutcome.CONSUMED).count());
                    assertEquals(1, store.ticketTakeSuccesses());
                }));
    }

    private static void executeVerifyVector(JsonNode root, JsonNode vector) {
        TestStateStore store = new TestStateStore();
        configureVerifyStore(root, vector, store);
        TestSupport.CollectingSecurityEventSink events = new TestSupport.CollectingSecurityEventSink();
        ChallengeVerifier verifier = verifier(root, store, vector.required("now").longValue(), events);
        VerifyResult result = verifier.verify(verifyCommand(root, vector.required("request")));
        JsonNode expected = vector.required("expected");

        assertEquals(VerifyOutcome.valueOf(expected.required("outcome").textValue()), result.outcome());
        assertEquals(1, store.challengeTakeCalls(), "Core must not transparently retry take");
        if (expected.required("challengePresentAfter").isBoolean()) {
            JsonNode challenge = root.required("fixtures").required("challenge");
            assertEquals(
                    expected.required("challengePresentAfter").booleanValue(),
                    store.hasChallenge(
                            new SiteKey(challenge.required("state").required("siteKey").textValue()),
                            new ChallengeId(challenge.required("challengeId").textValue())));
        }
        if (expected.has("ticketReturned") && !expected.required("ticketReturned").booleanValue()) {
            assertTrue(result.verificationTicket().isEmpty());
        }
        if (result.outcome() == VerifyOutcome.TICKET_ISSUED) {
            assertTrue(result.verificationTicket().isPresent());
            assertEquals(1, store.ticketStoreSuccesses());
        }
        assertInternalReason(expected, events.events());
    }

    private static void executeConsumeVector(JsonNode root, JsonNode vector) {
        TestStateStore store = new TestStateStore();
        configureConsumeStore(root, vector, store);
        TestSupport.CollectingSecurityEventSink events = new TestSupport.CollectingSecurityEventSink();
        TicketConsumer consumer = new TicketConsumer(
                store, siteRegistry(root), fixedClock(vector.required("now").longValue()), events);
        ConsumeResult result = consumer.consume(consumeCommand(vector.required("request")));
        JsonNode expected = vector.required("expected");

        assertEquals(ConsumeOutcome.valueOf(expected.required("outcome").textValue()), result.outcome());
        assertEquals(1, store.ticketTakeCalls(), "Core must not transparently retry take");
        if (expected.required("ticketPresentAfter").isBoolean()) {
            VerificationTicket ticket = new VerificationTicket(root.required("fixtures").required("ticket")
                    .required("verificationTicket").textValue());
            assertEquals(expected.required("ticketPresentAfter").booleanValue(),
                    store.hasTicket(TicketDigest.from(ticket)));
        }
        assertInternalReason(expected, events.events());
    }

    private static void configureVerifyStore(JsonNode root, JsonNode vector, TestStateStore store) {
        String mode = vector.required("stateStore").textValue();
        if (mode.startsWith("PRESENT")) {
            seedChallenge(root, store);
        }
        if (mode.equals("TAKE_RESULT_UNKNOWN")) {
            store.challengeTakeMode(TestStateStore.TakeMode.UNKNOWN);
        }
        if (mode.equals("TAKE_FAILED")) {
            seedChallenge(root, store);
            store.challengeTakeMode(TestStateStore.TakeMode.FAILED);
        }
        if (mode.equals("PRESENT_THEN_TICKET_WRITE_FAILED")) {
            store.ticketStoreResult(StoreTicketResult.FAILED);
        }
        if (mode.equals("PRESENT_THEN_TICKET_WRITE_UNKNOWN")) {
            store.ticketStoreResult(StoreTicketResult.UNKNOWN);
        }
    }

    private static void configureConsumeStore(JsonNode root, JsonNode vector, TestStateStore store) {
        String mode = vector.required("stateStore").textValue();
        if (mode.equals("PRESENT")) {
            seedTicket(root, store);
        } else if (mode.equals("TAKE_RESULT_UNKNOWN")) {
            store.ticketTakeMode(TestStateStore.TakeMode.UNKNOWN);
        } else if (mode.equals("TAKE_FAILED")) {
            seedTicket(root, store);
            store.ticketTakeMode(TestStateStore.TakeMode.FAILED);
        }
    }

    private static ChallengeVerifier verifier(
            JsonNode root,
            TestStateStore store,
            long now,
            SecurityEventSink events) {
        JsonNode fixtures = root.required("fixtures");
        return new ChallengeVerifier(
                store,
                siteRegistry(root),
                fixedClock(now),
                new TestSupport.FixedTokenGenerator(
                        new ChallengeId(fixtures.required("challenge").required("challengeId").textValue()),
                new VerificationTicket(fixtures.required("ticket").required("verificationTicket").textValue())),
                events);
    }

    private static VerifyChallengeCommand validVerifyCommand(JsonNode root) {
        return verifyCommand(root, root.required("verifyVectors").get(0).required("request"));
    }

    private static VerifyChallengeCommand verifyCommand(JsonNode root, JsonNode request) {
        JsonNode solution = request.required("solution");
        return new VerifyChallengeCommand(
                ProtocolVersion.fromWireValue(request.required("protocolVersion").textValue()),
                new SiteKey(request.required("siteKey").textValue()),
                new ChallengeId(request.required("challengeId").textValue()),
                solution.required("finalPieceX").longValue(),
                track(root, solution),
                CallerContext.trustedBackend());
    }

    private static Track track(JsonNode root, JsonNode solution) {
        JsonNode source = solution.has("track")
                ? solution.required("track")
                : root.required("fixtures").required("validTrack");
        List<TrackPoint> points = new ArrayList<>();
        for (JsonNode point : source) {
            points.add(new TrackPoint(
                    point.required("x").longValue(),
                    point.required("y").longValue(),
                    point.required("t").longValue(),
                    TrackEvent.valueOf(point.required("event").textValue())));
        }
        if (solution.has("trackFinalX")) {
            TrackPoint last = points.get(points.size() - 1);
            points.set(points.size() - 1, new TrackPoint(
                    solution.required("trackFinalX").longValue(), last.y(), last.t(), last.event()));
        }
        return new Track(points);
    }

    private static ConsumeTicketCommand consumeCommand(JsonNode request) {
        return new ConsumeTicketCommand(
                ProtocolVersion.fromWireValue(request.required("protocolVersion").textValue()),
                new VerificationTicket(request.required("verificationTicket").textValue()),
                new SiteKey(request.required("siteKey").textValue()),
                new ActionName(request.required("action").textValue()),
                new ContextDigest(request.required("contextDigest").textValue()));
    }

    private static SiteRegistry siteRegistry(JsonNode root) {
        JsonNode state = root.required("fixtures").required("challenge").required("state");
        SiteRegistration registration = new SiteRegistration(
                new SiteKey(state.required("siteKey").textValue()),
                "Vector Site",
                SiteStatus.ACTIVE,
                new SitePolicy(
                        Duration.ofSeconds(120),
                        Duration.ofSeconds(60),
                        state.required("policyVersion").textValue(),
                        Set.of(new ActionName("login"), new ActionName("password-reset")),
                        Set.of(),
                        false));
        return siteKey -> registration.siteKey().equals(siteKey)
                ? java.util.Optional.of(registration)
                : java.util.Optional.empty();
    }

    private static void seedChallenge(JsonNode root, TestStateStore store) {
        JsonNode fixture = root.required("fixtures").required("challenge");
        JsonNode state = fixture.required("state");
        JsonNode geometry = state.required("geometry");
        SiteKey siteKey = new SiteKey(state.required("siteKey").textValue());
        ChallengeId challengeId = new ChallengeId(fixture.required("challengeId").textValue());
        store.seedChallenge(siteKey, challengeId, new ChallengeState(
                state.required("storageVersion").intValue(),
                ProtocolVersion.fromWireValue(state.required("protocolVersion").textValue()),
                ChallengeType.valueOf(state.required("challengeType").textValue()),
                siteKey,
                new ActionName(state.required("action").textValue()),
                new ContextDigest(state.required("contextDigest").textValue()),
                state.required("issuedAt").longValue(),
                state.required("expiresAt").longValue(),
                new SliderPuzzleGeometry(
                        geometry.required("pieceStartX").longValue(),
                        geometry.required("pieceTargetX").longValue(),
                        geometry.required("pieceStartY").longValue(),
                        geometry.required("pieceWidth").longValue(),
                        geometry.required("pieceHeight").longValue(),
                        geometry.required("tolerance").longValue()),
                state.required("policyVersion").textValue()));
    }

    private static void seedTicket(JsonNode root, TestStateStore store) {
        JsonNode fixture = root.required("fixtures").required("ticket");
        JsonNode state = fixture.required("state");
        VerificationTicket ticket = new VerificationTicket(fixture.required("verificationTicket").textValue());
        store.seedTicket(TicketDigest.from(ticket), new TicketState(
                state.required("storageVersion").intValue(),
                ProtocolVersion.fromWireValue(state.required("protocolVersion").textValue()),
                new SiteKey(state.required("siteKey").textValue()),
                new ActionName(state.required("action").textValue()),
                new ContextDigest(state.required("contextDigest").textValue()),
                ChallengeType.valueOf(state.required("challengeType").textValue()),
                state.required("policyVersion").textValue(),
                state.required("verifiedAt").longValue(),
                state.required("issuedAt").longValue(),
                state.required("expiresAt").longValue()));
    }

    private static void assertInternalReason(JsonNode expected, List<SecurityEvent> events) {
        if (expected.has("internalReason")) {
            assertFalse(events.isEmpty());
            assertEquals(expected.required("internalReason").textValue(),
                    events.get(events.size() - 1).reason().name());
        }
    }

    private static boolean referenceParserAccepts(String rawJson) {
        try {
            JsonNode request = STRICT_OBJECT_MAPPER.readTree(rawJson);
            if (!request.isObject() || !hasExactlyFields(
                    request, Set.of("protocolVersion", "siteKey", "challengeId", "solution"))) {
                return false;
            }
            ProtocolVersion.fromWireValue(request.required("protocolVersion").textValue());
            new SiteKey(request.required("siteKey").textValue());
            new ChallengeId(request.required("challengeId").textValue());
            JsonNode solution = request.required("solution");
            if (!solution.isObject()) {
                return false;
            }
            JsonNode finalPieceX = solution.get("finalPieceX");
            return finalPieceX == null || finalPieceX.isIntegralNumber();
        } catch (RuntimeException | IOException exception) {
            return false;
        }
    }

    private static boolean hasExactlyFields(JsonNode object, Set<String> allowed) {
        Set<String> actual = new HashSet<>();
        Iterator<String> names = object.fieldNames();
        names.forEachRemaining(actual::add);
        return actual.equals(allowed);
    }

    private static <T> List<T> runTogether(ThrowingSupplier<T> operation) throws Exception {
        return runConcurrent(2, operation);
    }

    private static <T> List<T> runConcurrent(int callerCount, ThrowingSupplier<T> operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        CyclicBarrier barrier = new CyclicBarrier(callerCount);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < callerCount; index++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return operation.get();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return List.copyOf(results);
        } finally {
            executor.shutdownNow();
        }
    }

    private static Clock fixedClock(long epochMillis) {
        return Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private static Stream<JsonNode> stream(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }

    private static JsonNode loadVectors() throws IOException {
        try (InputStream input = ProtocolVectorTest.class.getResourceAsStream("/protocol-v1.json")) {
            if (input == null) {
                throw new IllegalStateException("Protocol vectors are missing from the test classpath");
            }
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static void validateVectorHeader(JsonNode root) {
        assertEquals("chalsense-protocol-v1", root.required("vectorSet").textValue());
        assertEquals("approved-v0.1", root.required("status").textValue());
        assertEquals("1", root.required("protocolVersion").textValue());
        assertEquals(1_000_000L, root.required("coordinateScale").longValue());
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
