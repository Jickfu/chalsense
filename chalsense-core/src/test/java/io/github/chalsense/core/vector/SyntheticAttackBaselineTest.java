package io.github.chalsense.core.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.chalsense.core.site.CallerContext;
import io.github.chalsense.core.site.SitePolicy;
import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.core.site.SiteRegistry;
import io.github.chalsense.core.site.SiteStatus;
import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.SliderPuzzleGeometry;
import io.github.chalsense.core.verify.ChallengeVerifier;
import io.github.chalsense.core.verify.VerifyChallengeCommand;
import io.github.chalsense.core.verify.VerifyOutcome;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins known synthetic attack behavior without treating synthetic success or failure as proof of humanness. */
class SyntheticAttackBaselineTest {
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final SiteKey SITE_KEY = new SiteKey("site_demo_01");
    private static final ChallengeId CHALLENGE_ID = new ChallengeId("AAAAAAAAAAAAAAAAAAAAAA");
    private static final VerificationTicket TICKET = new VerificationTicket(
            "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE");
    private static final ActionName ACTION = new ActionName("login");
    private static final long NOW = 1_787_356_801_000L;

    @TestFactory
    Stream<DynamicTest> preservesKnownSyntheticAttackOutcomes() throws IOException {
        JsonNode root = load();
        return StreamSupport.stream(root.required("vectors").spliterator(), false)
                .map(vector -> DynamicTest.dynamicTest(vector.required("id").textValue(), () -> {
                    TestStateStore store = seededStore();
                    VerifyOutcome outcome = verifier(store).verify(command(vector)).outcome();
                    assertEquals(VerifyOutcome.valueOf(vector.required("expectedOutcome").textValue()), outcome);
                }));
    }

    @Test
    void consumesChallengeBeforeRejectingReplay() throws IOException {
        JsonNode root = load();
        JsonNode replay = root.required("replay");
        JsonNode source = StreamSupport.stream(root.required("vectors").spliterator(), false)
                .filter(vector -> vector.required("id").textValue().equals(replay.required("sourceVector").textValue()))
                .findFirst().orElseThrow();
        TestStateStore store = seededStore();
        ChallengeVerifier verifier = verifier(store);

        assertEquals(VerifyOutcome.valueOf(replay.required("expectedFirstOutcome").textValue()),
                verifier.verify(command(source)).outcome());
        assertEquals(VerifyOutcome.valueOf(replay.required("expectedSecondOutcome").textValue()),
                verifier.verify(command(source)).outcome());
    }

    private static TestStateStore seededStore() {
        TestStateStore store = new TestStateStore();
        store.seedChallenge(SITE_KEY, CHALLENGE_ID, new ChallengeState(
                1, ProtocolVersion.V1, ChallengeType.SLIDER_PUZZLE, SITE_KEY, ACTION,
                new ContextDigest("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                NOW - 1_000, NOW + 119_000,
                new SliderPuzzleGeometry(62_500, 593_750, 388_889, 156_250, 277_778, 12_500),
                "slider-draft-1"));
        return store;
    }

    private static ChallengeVerifier verifier(TestStateStore store) {
        SiteRegistration registration = new SiteRegistration(
                SITE_KEY, "Attack Baseline", SiteStatus.ACTIVE,
                new SitePolicy(Duration.ofSeconds(120), Duration.ofSeconds(60), "slider-draft-1",
                        Set.of(ACTION), Set.of(), false));
        SiteRegistry registry = siteKey -> siteKey.equals(SITE_KEY)
                ? Optional.of(registration) : Optional.empty();
        return new ChallengeVerifier(
                store, registry, Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC),
                new TestSupport.FixedTokenGenerator(CHALLENGE_ID, TICKET),
                new TestSupport.CollectingSecurityEventSink());
    }

    private static VerifyChallengeCommand command(JsonNode vector) {
        List<TrackPoint> points = new ArrayList<>();
        for (JsonNode point : vector.required("track")) {
            points.add(new TrackPoint(
                    point.required("x").longValue(), point.required("y").longValue(),
                    point.required("t").longValue(), TrackEvent.valueOf(point.required("event").textValue())));
        }
        return new VerifyChallengeCommand(
                ProtocolVersion.V1, SITE_KEY, CHALLENGE_ID,
                vector.required("finalPieceX").longValue(), new Track(points), CallerContext.trustedBackend());
    }

    private static JsonNode load() throws IOException {
        try (InputStream stream = SyntheticAttackBaselineTest.class.getResourceAsStream("/attack-baseline-v1.json")) {
            if (stream == null) throw new IOException("attack baseline resource missing");
            return OBJECT_MAPPER.readTree(stream);
        }
    }
}
