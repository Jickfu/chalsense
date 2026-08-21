package io.github.chalsense.core.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chalsense.core.TokenGenerator;
import io.github.chalsense.core.challenge.ChallengeCreator;
import io.github.chalsense.core.challenge.ChallengeGenerator;
import io.github.chalsense.core.challenge.ChallengeResource;
import io.github.chalsense.core.challenge.ChallengeResourceRole;
import io.github.chalsense.core.challenge.CreateChallengeCommand;
import io.github.chalsense.core.challenge.CreateOutcome;
import io.github.chalsense.core.challenge.CreateResult;
import io.github.chalsense.core.challenge.GeneratedChallenge;
import io.github.chalsense.core.security.SecurityEventSink;
import io.github.chalsense.core.site.CallerContext;
import io.github.chalsense.core.site.SitePolicy;
import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.core.site.SiteRegistry;
import io.github.chalsense.core.site.SiteStatus;
import io.github.chalsense.core.site.WebOrigin;
import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.SliderPuzzleGeometry;
import io.github.chalsense.core.state.StateStore;
import io.github.chalsense.core.state.StoreChallengeResult;
import io.github.chalsense.core.state.StoreTicketResult;
import io.github.chalsense.core.state.TakeResult;
import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.VerificationTicket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChallengeCreateVectorTest {
    private static JsonNode root;
    private static JsonNode fixtures;

    @BeforeAll
    static void loadVectors() throws IOException {
        try (InputStream input = ChallengeCreateVectorTest.class.getResourceAsStream(
                "/challenge-create-v1.json")) {
            root = new ObjectMapper().readTree(input);
            fixtures = root.required("fixtures");
        }
        assertEquals("chalsense-challenge-create-v1", root.required("vectorSet").textValue());
        assertEquals("approved-v0.1", root.required("status").textValue());
    }

    @TestFactory
    Stream<DynamicTest> createVectors() {
        List<JsonNode> vectors = new ArrayList<>();
        root.required("createVectors").forEach(vectors::add);
        return vectors.stream().map(vector -> DynamicTest.dynamicTest(
                vector.required("id").textValue(), () -> execute(vector)));
    }

    private static void execute(JsonNode vector) {
        SiteKey siteKey = new SiteKey(fixtures.required("siteKey").textValue());
        ActionName allowedAction = new ActionName(fixtures.required("allowedAction").textValue());
        WebOrigin allowedOrigin = WebOrigin.parse(fixtures.required("allowedOrigin").textValue());
        SitePolicy policy = new SitePolicy(
                Duration.ofMillis(fixtures.required("challengeTtlMillis").longValue()),
                Duration.ofSeconds(60),
                fixtures.required("policyVersion").textValue(),
                Set.of(allowedAction),
                Set.of(allowedOrigin),
                false);
        SiteRegistry registry = registry(vector.required("site").textValue(), siteKey, policy);
        CallerContext caller = caller(vector, allowedOrigin);
        ScriptedStore store = new ScriptedStore(storeResults(vector.required("storeResults")));
        AtomicInteger generatorCalls = new AtomicInteger();
        ChallengeGenerator generator = request -> {
            generatorCalls.incrementAndGet();
            if (vector.path("generator").asText("CONFIRMED").equals("FAILED")) {
                throw new IllegalStateException("scripted generator failure");
            }
            return generated();
        };
        List<ChallengeId> challengeIds = new ArrayList<>();
        fixtures.required("challengeIds").forEach(node -> challengeIds.add(new ChallengeId(node.textValue())));
        long now = fixtures.required("now").longValue();
        ChallengeCreator creator = new ChallengeCreator(
                store,
                registry,
                Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
                tokenGenerator(challengeIds),
                generator,
                SecurityEventSink.noop());

        CreateResult result = creator.create(new CreateChallengeCommand(
                ProtocolVersion.V1,
                siteKey,
                new ActionName(vector.required("action").textValue()),
                new ContextDigest(fixtures.required("contextDigest").textValue()),
                caller));
        JsonNode expected = vector.required("expected");
        assertEquals(CreateOutcome.valueOf(expected.required("outcome").textValue()), result.outcome());
        assertEquals(expected.required("generatorCalls").intValue(), generatorCalls.get());
        assertEquals(expected.required("storeCalls").intValue(), store.calls);
        assertEquals(expected.required("challengePresentAfter").booleanValue(), store.challengePresent);

        if (result.outcome() == CreateOutcome.CHALLENGE_CREATED) {
            int idIndex = expected.required("challengeIdIndex").intValue();
            var created = result.challenge().orElseThrow();
            assertEquals(challengeIds.get(idIndex), created.challengeId());
            assertEquals(now, created.issuedAt());
            assertEquals(now + fixtures.required("challengeTtlMillis").longValue(), created.expiresAt());
            assertEquals(fixtures.required("generated").required("logicalWidth").intValue(),
                    created.geometry().logicalWidth());
            assertEquals(fixtures.required("generated").required("geometry").required("pieceStartX").longValue(),
                    created.geometry().pieceStartX());
            assertEquals(2, created.resources().size());
            assertTrue(store.lastState.isPresent());
            assertEquals(fixtures.required("generated").required("geometry").required("pieceTargetX").longValue(),
                    store.lastState.orElseThrow().geometry().pieceTargetX());
        } else {
            assertFalse(result.challenge().isPresent());
        }
    }

    private static SiteRegistry registry(String script, SiteKey siteKey, SitePolicy policy) {
        if (script.equals("UNKNOWN")) {
            return ignored -> Optional.empty();
        }
        SiteStatus status = SiteStatus.valueOf(script);
        SiteRegistration site = new SiteRegistration(siteKey, "Vector site", status, policy);
        return requested -> requested.equals(siteKey) ? Optional.of(site) : Optional.empty();
    }

    private static CallerContext caller(JsonNode vector, WebOrigin allowedOrigin) {
        if (vector.required("caller").textValue().equals("TRUSTED_BACKEND")) {
            return CallerContext.trustedBackend();
        }
        return CallerContext.publicBrowser(WebOrigin.parse(
                vector.path("origin").asText(allowedOrigin.value())));
    }

    private static Queue<StoreChallengeResult> storeResults(JsonNode values) {
        Queue<StoreChallengeResult> results = new ArrayDeque<>();
        values.forEach(node -> results.add(StoreChallengeResult.valueOf(node.textValue())));
        return results;
    }

    private static GeneratedChallenge generated() {
        JsonNode generated = fixtures.required("generated");
        JsonNode geometry = generated.required("geometry");
        List<ChallengeResource> resources = new ArrayList<>();
        generated.required("resources").forEach(resource -> resources.add(new ChallengeResource(
                ChallengeResourceRole.valueOf(resource.required("role").textValue()),
                resource.required("url").textValue(),
                resource.required("mediaType").textValue(),
                resource.required("pixelWidth").intValue(),
                resource.required("pixelHeight").intValue())));
        return new GeneratedChallenge(
                new SliderPuzzleGeometry(
                        geometry.required("pieceStartX").longValue(),
                        geometry.required("pieceTargetX").longValue(),
                        geometry.required("pieceStartY").longValue(),
                        geometry.required("pieceWidth").longValue(),
                        geometry.required("pieceHeight").longValue(),
                        geometry.required("tolerance").longValue()),
                generated.required("logicalWidth").intValue(),
                generated.required("logicalHeight").intValue(),
                resources);
    }

    private static TokenGenerator tokenGenerator(List<ChallengeId> ids) {
        Iterator<ChallengeId> iterator = ids.iterator();
        return new TokenGenerator() {
            @Override
            public ChallengeId newChallengeId() {
                return iterator.next();
            }

            @Override
            public VerificationTicket newVerificationTicket() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class ScriptedStore implements StateStore {
        private final Queue<StoreChallengeResult> results;
        private int calls;
        private boolean challengePresent;
        private Optional<ChallengeState> lastState = Optional.empty();

        private ScriptedStore(Queue<StoreChallengeResult> results) {
            this.results = results;
        }

        @Override
        public StoreChallengeResult storeChallengeIfAbsent(
                SiteKey siteKey, ChallengeId challengeId, ChallengeState challengeState) {
            calls++;
            StoreChallengeResult result = results.remove();
            if (result == StoreChallengeResult.CONFIRMED) {
                challengePresent = true;
                lastState = Optional.of(challengeState);
            }
            return result;
        }

        @Override
        public TakeResult<ChallengeState> takeChallenge(SiteKey siteKey, ChallengeId challengeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoreTicketResult storeTicketIfAbsent(TicketDigest ticketDigest, TicketState ticketState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TakeResult<TicketState> takeTicket(TicketDigest ticketDigest) {
            throw new UnsupportedOperationException();
        }
    }
}
