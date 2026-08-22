package io.github.chalsense.core.challenge;

import io.github.chalsense.core.TokenGenerator;
import io.github.chalsense.core.security.SecurityEvent;
import io.github.chalsense.core.security.SecurityEventSink;
import io.github.chalsense.core.security.SecurityOperation;
import io.github.chalsense.core.security.SecurityReason;
import io.github.chalsense.core.site.SiteAuthorization;
import io.github.chalsense.core.site.SiteAuthorizer;
import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.core.site.SiteRegistry;
import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.StateStore;
import io.github.chalsense.core.state.StoreChallengeResult;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ChallengeType;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Creates a challenge only after caller checks and confirmed atomic persistence. */
public final class ChallengeCreator {
    private static final int CURRENT_STORAGE_VERSION = 1;
    private static final int MAXIMUM_ID_ATTEMPTS = 3;

    private final StateStore stateStore;
    private final SiteRegistry siteRegistry;
    private final Clock clock;
    private final TokenGenerator tokenGenerator;
    private final ChallengeGenerator challengeGenerator;
    private final SecurityEventSink securityEventSink;

    public ChallengeCreator(
            StateStore stateStore,
            SiteRegistry siteRegistry,
            Clock clock,
            TokenGenerator tokenGenerator,
            ChallengeGenerator challengeGenerator,
            SecurityEventSink securityEventSink) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.siteRegistry = Objects.requireNonNull(siteRegistry, "siteRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator");
        this.challengeGenerator = Objects.requireNonNull(challengeGenerator, "challengeGenerator");
        this.securityEventSink = Objects.requireNonNull(securityEventSink, "securityEventSink");
    }

    public CreateResult create(CreateChallengeCommand command) {
        Objects.requireNonNull(command, "command");
        Optional<SiteRegistration> resolvedSite = siteRegistry.find(command.siteKey());
        if (resolvedSite.isEmpty()) {
            return fail(CreateOutcome.CALLER_UNAUTHORIZED, SecurityReason.CALLER_UNAUTHORIZED);
        }
        SiteRegistration registration = resolvedSite.get();
        SiteAuthorization authorization = SiteAuthorizer.authorizeCaller(registration, command.callerContext());
        if (authorization == SiteAuthorization.CALLER_UNAUTHORIZED) {
            return fail(CreateOutcome.CALLER_UNAUTHORIZED, SecurityReason.CALLER_UNAUTHORIZED);
        }
        if (authorization == SiteAuthorization.ORIGIN_NOT_ALLOWED) {
            return fail(CreateOutcome.ORIGIN_NOT_ALLOWED, SecurityReason.ORIGIN_NOT_ALLOWED);
        }
        if (!SiteAuthorizer.allowsAction(registration, command.action())) {
            return fail(CreateOutcome.CALLER_UNAUTHORIZED, SecurityReason.ACTION_NOT_ALLOWED);
        }

        long issuedAt = clock.millis();
        final long expiresAt;
        try {
            expiresAt = Math.addExact(issuedAt, registration.policy().challengeTtl().toMillis());
        } catch (ArithmeticException exception) {
            return fail(CreateOutcome.DEPENDENCY_UNAVAILABLE, SecurityReason.GENERATOR_FAILED);
        }

        for (int attempt = 0; attempt < MAXIMUM_ID_ATTEMPTS; attempt++) {
            ChallengeId challengeId = Objects.requireNonNull(
                    tokenGenerator.newChallengeId(), "tokenGenerator returned null");
            ChallengeGenerationRequest generationRequest = new ChallengeGenerationRequest(
                    registration, command.action(), challengeId, issuedAt, expiresAt);
            final GeneratedChallenge generated;
            try {
                generated = Objects.requireNonNull(
                        challengeGenerator.generate(generationRequest),
                        "challengeGenerator returned null");
            } catch (RuntimeException exception) {
                return fail(CreateOutcome.DEPENDENCY_UNAVAILABLE, SecurityReason.GENERATOR_FAILED);
            }

            ChallengeState state = new ChallengeState(
                    CURRENT_STORAGE_VERSION,
                    command.protocolVersion(),
                    ChallengeType.SLIDER_PUZZLE,
                    command.siteKey(),
                    command.action(),
                    command.contextDigest(),
                    issuedAt,
                    expiresAt,
                    generated.geometry(),
                    registration.policy().policyVersion());
            final StoreChallengeResult storeResult;
            try {
                storeResult = Objects.requireNonNull(stateStore.storeChallengeIfAbsent(
                        command.siteKey(), challengeId, state), "stateStore returned null");
            } catch (RuntimeException exception) {
                discard(generationRequest, generated);
                return fail(CreateOutcome.DEPENDENCY_UNAVAILABLE, SecurityReason.STORE_FAILED);
            }
            if (storeResult == StoreChallengeResult.CONFIRMED) {
                return CreateResult.created(new CreatedChallenge(
                        challengeId,
                        ChallengeType.SLIDER_PUZZLE,
                        issuedAt,
                        expiresAt,
                        PublicSliderGeometry.from(generated),
                        generated.resources()));
            }
            if (storeResult == StoreChallengeResult.ALREADY_EXISTS) {
                discard(generationRequest, generated);
                record(SecurityReason.CHALLENGE_ID_COLLISION);
                continue;
            }
            discard(generationRequest, generated);
            return fail(
                    CreateOutcome.DEPENDENCY_UNAVAILABLE,
                    storeResult == StoreChallengeResult.UNKNOWN
                            ? SecurityReason.STORE_RESULT_UNKNOWN
                            : SecurityReason.STORE_FAILED);
        }
        return CreateResult.failure(CreateOutcome.DEPENDENCY_UNAVAILABLE);
    }

    private void discard(ChallengeGenerationRequest request, GeneratedChallenge generated) {
        try {
            challengeGenerator.discard(request, generated);
        } catch (RuntimeException ignored) {
            // Publisher hard TTL remains the final cleanup boundary.
        }
    }

    private CreateResult fail(CreateOutcome outcome, SecurityReason reason) {
        record(reason);
        return CreateResult.failure(outcome);
    }

    private void record(SecurityReason reason) {
        try {
            securityEventSink.record(new SecurityEvent(SecurityOperation.CHALLENGE_CREATE, reason));
        } catch (RuntimeException ignored) {
            // Observability must not alter challenge issuance semantics.
        }
    }
}
