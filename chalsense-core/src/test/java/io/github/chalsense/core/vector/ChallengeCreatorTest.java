package io.github.chalsense.core.vector;

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
import io.github.chalsense.core.state.SliderPuzzleGeometry;
import io.github.chalsense.core.state.StoreChallengeResult;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.VerificationTicket;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChallengeCreatorTest {
    private static final SiteKey SITE_KEY = new SiteKey("site_test");
    private static final ActionName LOGIN = new ActionName("login");
    private static final ActionName REGISTER = new ActionName("register");
    private static final ContextDigest CONTEXT = new ContextDigest(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    private static final ChallengeId ID_A = new ChallengeId("AAAAAAAAAAAAAAAAAAAAAA");
    private static final ChallengeId ID_B = new ChallengeId("AQEBAQEBAQEBAQEBAQEBAQ");
    private static final WebOrigin ALLOWED_ORIGIN = WebOrigin.parse("https://app.example.com");
    private static final long NOW = 1_700_000_000_000L;

    @Test
    void createsAndPersistsServerAuthoritativeChallenge() {
        TestStateStore store = new TestStateStore();
        CreateResult result = creator(store, activeRegistry(), tokens(ID_A), generator(new AtomicInteger()))
                .create(command(LOGIN, CallerContext.trustedBackend()));

        assertEquals(CreateOutcome.CHALLENGE_CREATED, result.outcome());
        assertTrue(result.challenge().isPresent());
        var created = result.challenge().orElseThrow();
        assertEquals(ID_A, created.challengeId());
        assertEquals(NOW, created.issuedAt());
        assertEquals(NOW + 120_000, created.expiresAt());
        assertEquals(100_000, created.geometry().pieceStartX());
        assertEquals(1_000_000, created.geometry().coordinateScale());
        assertEquals(1, store.challengeStoreSuccesses());
        var state = store.challengeState(SITE_KEY, ID_A).orElseThrow();
        assertEquals(700_000, state.geometry().pieceTargetX());
        assertEquals(20_000, state.geometry().tolerance());
    }

    @Test
    void acceptsOnlyExactlyRegisteredBrowserOrigin() {
        TestStateStore allowedStore = new TestStateStore();
        CreateResult allowed = creator(allowedStore, activeRegistry(), tokens(ID_A), generator(new AtomicInteger()))
                .create(command(LOGIN, CallerContext.publicBrowser(ALLOWED_ORIGIN)));
        assertEquals(CreateOutcome.CHALLENGE_CREATED, allowed.outcome());

        AtomicInteger calls = new AtomicInteger();
        TestStateStore deniedStore = new TestStateStore();
        CreateResult denied = creator(deniedStore, activeRegistry(), tokens(ID_A), generator(calls))
                .create(command(LOGIN, CallerContext.publicBrowser(WebOrigin.parse("https://other.example.com"))));
        assertEquals(CreateOutcome.ORIGIN_NOT_ALLOWED, denied.outcome());
        assertEquals(0, calls.get());
        assertEquals(0, deniedStore.challengeStoreCalls());
    }

    @Test
    void rejectsUnknownDisabledAndDisallowedActionBeforeGeneration() {
        assertPrecheckRejected(siteKey -> Optional.empty(), LOGIN, CreateOutcome.CALLER_UNAUTHORIZED);
        assertPrecheckRejected(registry(SiteStatus.DISABLED), LOGIN, CreateOutcome.CALLER_UNAUTHORIZED);
        assertPrecheckRejected(activeRegistry(), REGISTER, CreateOutcome.CALLER_UNAUTHORIZED);
    }

    @Test
    void retriesConfirmedCollisionWithANewIdentifier() {
        TestStateStore store = new TestStateStore();
        store.seedChallenge(SITE_KEY, ID_A, existingState());
        AtomicInteger generatorCalls = new AtomicInteger();

        CreateResult result = creator(store, activeRegistry(), tokens(ID_A, ID_B), generator(generatorCalls))
                .create(command(LOGIN, CallerContext.trustedBackend()));

        assertEquals(CreateOutcome.CHALLENGE_CREATED, result.outcome());
        assertEquals(ID_B, result.challenge().orElseThrow().challengeId());
        assertEquals(2, generatorCalls.get());
        assertEquals(2, store.challengeStoreCalls());
    }

    @Test
    void stopsAfterThreeConfirmedCollisions() {
        TestStateStore store = new TestStateStore();
        store.seedChallenge(SITE_KEY, ID_A, existingState());
        AtomicInteger generatorCalls = new AtomicInteger();

        CreateResult result = creator(store, activeRegistry(), tokens(ID_A, ID_A, ID_A), generator(generatorCalls))
                .create(command(LOGIN, CallerContext.trustedBackend()));

        assertEquals(CreateOutcome.DEPENDENCY_UNAVAILABLE, result.outcome());
        assertFalse(result.challenge().isPresent());
        assertEquals(3, generatorCalls.get());
        assertEquals(3, store.challengeStoreCalls());
    }

    @Test
    void neverRetriesOrReturnsCredentialWhenStoreResultIsUnknown() {
        TestStateStore store = new TestStateStore();
        store.challengeStoreResult(StoreChallengeResult.UNKNOWN);
        AtomicInteger generatorCalls = new AtomicInteger();

        AtomicInteger discardCalls = new AtomicInteger();
        ChallengeGenerator generator = trackingGenerator(generatorCalls, discardCalls);
        CreateResult result = creator(store, activeRegistry(), tokens(ID_A, ID_B), generator)
                .create(command(LOGIN, CallerContext.trustedBackend()));

        assertEquals(CreateOutcome.DEPENDENCY_UNAVAILABLE, result.outcome());
        assertFalse(result.challenge().isPresent());
        assertEquals(1, generatorCalls.get());
        assertEquals(1, discardCalls.get());
        assertEquals(1, store.challengeStoreCalls());
    }

    @Test
    void generatorFailureDoesNotTouchStateStore() {
        TestStateStore store = new TestStateStore();
        ChallengeGenerator failing = request -> { throw new IllegalStateException("unavailable"); };

        CreateResult result = creator(store, activeRegistry(), tokens(ID_A), failing)
                .create(command(LOGIN, CallerContext.trustedBackend()));

        assertEquals(CreateOutcome.DEPENDENCY_UNAVAILABLE, result.outcome());
        assertEquals(0, store.challengeStoreCalls());
    }

    private static void assertPrecheckRejected(
            SiteRegistry registry, ActionName action, CreateOutcome expected) {
        TestStateStore store = new TestStateStore();
        AtomicInteger calls = new AtomicInteger();
        CreateResult result = creator(store, registry, tokens(ID_A), generator(calls))
                .create(command(action, CallerContext.trustedBackend()));
        assertEquals(expected, result.outcome());
        assertEquals(0, calls.get());
        assertEquals(0, store.challengeStoreCalls());
    }

    private static ChallengeCreator creator(
            TestStateStore store,
            SiteRegistry registry,
            TokenGenerator tokens,
            ChallengeGenerator generator) {
        return new ChallengeCreator(
                store,
                registry,
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC),
                tokens,
                generator,
                SecurityEventSink.noop());
    }

    private static CreateChallengeCommand command(ActionName action, CallerContext caller) {
        return new CreateChallengeCommand(ProtocolVersion.V1, SITE_KEY, action, CONTEXT, caller);
    }

    private static SiteRegistry activeRegistry() {
        return registry(SiteStatus.ACTIVE);
    }

    private static SiteRegistry registry(SiteStatus status) {
        SitePolicy policy = new SitePolicy(
                Duration.ofSeconds(120),
                Duration.ofSeconds(60),
                "policy-v1",
                Set.of(LOGIN),
                Set.of(ALLOWED_ORIGIN),
                false);
        SiteRegistration registration = new SiteRegistration(SITE_KEY, "Test site", status, policy);
        return siteKey -> siteKey.equals(SITE_KEY) ? Optional.of(registration) : Optional.empty();
    }

    private static ChallengeGenerator generator(AtomicInteger calls) {
        return request -> {
            calls.incrementAndGet();
            return generated();
        };
    }

    private static ChallengeGenerator trackingGenerator(AtomicInteger calls, AtomicInteger discardCalls) {
        return new ChallengeGenerator() {
            @Override
            public GeneratedChallenge generate(io.github.chalsense.core.challenge.ChallengeGenerationRequest request) {
                calls.incrementAndGet();
                return generated();
            }

            @Override
            public void discard(
                    io.github.chalsense.core.challenge.ChallengeGenerationRequest request,
                    GeneratedChallenge generated) {
                discardCalls.incrementAndGet();
            }
        };
    }

    private static GeneratedChallenge generated() {
        return new GeneratedChallenge(
                new SliderPuzzleGeometry(100_000, 700_000, 200_000, 150_000, 200_000, 20_000),
                320,
                180,
                List.of(
                        new ChallengeResource(ChallengeResourceRole.BACKGROUND, "/r/background", "image/png", 640, 360),
                        new ChallengeResource(ChallengeResourceRole.PIECE, "/r/piece", "image/png", 96, 96)));
    }

    private static io.github.chalsense.core.state.ChallengeState existingState() {
        return new io.github.chalsense.core.state.ChallengeState(
                1, ProtocolVersion.V1, io.github.chalsense.protocol.ChallengeType.SLIDER_PUZZLE,
                SITE_KEY, LOGIN, CONTEXT, NOW, NOW + 120_000,
                generated().geometry(), "policy-v1");
    }

    private static TokenGenerator tokens(ChallengeId... challengeIds) {
        Queue<ChallengeId> ids = new ArrayDeque<>(List.of(challengeIds));
        return new TokenGenerator() {
            @Override
            public ChallengeId newChallengeId() {
                return ids.remove();
            }

            @Override
            public VerificationTicket newVerificationTicket() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
