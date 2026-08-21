package io.github.chalsense.core.verify;

import io.github.chalsense.core.TokenGenerator;
import io.github.chalsense.core.coordinate.CoordinateMath;
import io.github.chalsense.core.security.ConstantTime;
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
import io.github.chalsense.core.state.StoreTicketResult;
import io.github.chalsense.core.state.TakeResult;
import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.protocol.TrackPoint;
import io.github.chalsense.protocol.VerificationTicket;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Executes the approved consume-before-semantic-validation challenge state machine. */
public final class ChallengeVerifier {
    private static final int CURRENT_STORAGE_VERSION = 1;
    private static final long FINAL_POSITION_ROUNDING_TOLERANCE = 1;

    private final StateStore stateStore;
    private final SiteRegistry siteRegistry;
    private final Clock clock;
    private final TokenGenerator tokenGenerator;
    private final SecurityEventSink securityEventSink;

    public ChallengeVerifier(
            StateStore stateStore,
            SiteRegistry siteRegistry,
            Clock clock,
            TokenGenerator tokenGenerator,
            SecurityEventSink securityEventSink) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.siteRegistry = Objects.requireNonNull(siteRegistry, "siteRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator");
        this.securityEventSink = Objects.requireNonNull(securityEventSink, "securityEventSink");
    }

    public VerifyResult verify(VerifyChallengeCommand command) {
        Objects.requireNonNull(command, "command");
        Optional<SiteRegistration> resolvedSite = siteRegistry.find(command.siteKey());
        if (resolvedSite.isEmpty()) {
            record(SecurityReason.CALLER_UNAUTHORIZED);
            return VerifyResult.failure(VerifyOutcome.CALLER_UNAUTHORIZED);
        }
        SiteRegistration registration = resolvedSite.get();
        SiteAuthorization authorization = SiteAuthorizer.authorizeCaller(registration, command.callerContext());
        if (authorization == SiteAuthorization.CALLER_UNAUTHORIZED) {
            record(SecurityReason.CALLER_UNAUTHORIZED);
            return VerifyResult.failure(VerifyOutcome.CALLER_UNAUTHORIZED);
        }
        if (authorization == SiteAuthorization.ORIGIN_NOT_ALLOWED) {
            record(SecurityReason.ORIGIN_NOT_ALLOWED);
            return VerifyResult.failure(VerifyOutcome.ORIGIN_NOT_ALLOWED);
        }
        TakeResult<ChallengeState> takeResult = stateStore.takeChallenge(command.siteKey(), command.challengeId());
        if (takeResult instanceof TakeResult.Absent<ChallengeState>) {
            return VerifyResult.failure(VerifyOutcome.CHALLENGE_UNAVAILABLE);
        }
        if (takeResult instanceof TakeResult.Failed<ChallengeState>) {
            record(SecurityReason.STORE_FAILED);
            return VerifyResult.failure(VerifyOutcome.DEPENDENCY_UNAVAILABLE);
        }
        if (takeResult instanceof TakeResult.Unknown<ChallengeState>) {
            record(SecurityReason.STORE_RESULT_UNKNOWN);
            return VerifyResult.failure(VerifyOutcome.DEPENDENCY_UNAVAILABLE);
        }
        if (takeResult instanceof TakeResult.Unreadable<ChallengeState>) {
            record(SecurityReason.STORE_STATE_UNREADABLE);
            return VerifyResult.failure(VerifyOutcome.DEPENDENCY_UNAVAILABLE);
        }

        ChallengeState state = ((TakeResult.Present<ChallengeState>) takeResult).state();
        long now = clock.millis();
        if (now >= state.expiresAt()) {
            record(SecurityReason.EXPIRED);
            return VerifyResult.failure(VerifyOutcome.CHALLENGE_UNAVAILABLE);
        }
        if (state.storageVersion() != CURRENT_STORAGE_VERSION) {
            record(SecurityReason.STORE_FAILED);
            return VerifyResult.failure(VerifyOutcome.DEPENDENCY_UNAVAILABLE);
        }
        if (state.protocolVersion() != command.protocolVersion()) {
            return verificationFailed(SecurityReason.PROTOCOL_MISMATCH);
        }
        if (!ConstantTime.equalsUtf8(state.siteKey().value(), command.siteKey().value())) {
            return verificationFailed(SecurityReason.SITE_MISMATCH);
        }
        if (!SiteAuthorizer.allowsAction(registration, state.action())) {
            return verificationFailed(SecurityReason.ACTION_NOT_ALLOWED);
        }
        if (!TrackValidator.isStructurallyValid(command.track())) {
            return verificationFailed(SecurityReason.TRACK_STRUCTURE);
        }

        List<TrackPoint> points = command.track().points();
        TrackPoint finalPoint = points.get(points.size() - 1);
        long calculatedFinalPosition = CoordinateMath.piecePosition(
                state.geometry().pieceStartX(), finalPoint.x(), state.geometry().pieceWidth());
        if (!CoordinateMath.positionAccepted(
                command.finalPieceX(), calculatedFinalPosition, FINAL_POSITION_ROUNDING_TOLERANCE)) {
            return verificationFailed(SecurityReason.FINAL_POSITION_MISMATCH);
        }
        if (!CoordinateMath.positionAccepted(
                command.finalPieceX(), state.geometry().pieceTargetX(), state.geometry().tolerance())) {
            return verificationFailed(SecurityReason.ANSWER_MISMATCH);
        }

        VerificationTicket ticket = Objects.requireNonNull(
                tokenGenerator.newVerificationTicket(), "tokenGenerator returned null");
        long expiresAt = Math.addExact(now, registration.policy().ticketTtl().toMillis());
        TicketState ticketState = new TicketState(
                CURRENT_STORAGE_VERSION,
                state.protocolVersion(),
                state.siteKey(),
                state.action(),
                state.contextDigest(),
                state.challengeType(),
                state.policyVersion(),
                now,
                now,
                expiresAt);
        StoreTicketResult storeResult = stateStore.storeTicketIfAbsent(TicketDigest.from(ticket), ticketState);
        if (storeResult == StoreTicketResult.FAILED) {
            record(SecurityReason.TICKET_WRITE_FAILED);
            return VerifyResult.failure(VerifyOutcome.DEPENDENCY_UNAVAILABLE);
        }
        if (storeResult == StoreTicketResult.UNKNOWN) {
            record(SecurityReason.TICKET_WRITE_RESULT_UNKNOWN);
            return VerifyResult.failure(VerifyOutcome.DEPENDENCY_UNAVAILABLE);
        }
        return VerifyResult.ticketIssued(ticket, now, expiresAt);
    }

    private VerifyResult verificationFailed(SecurityReason reason) {
        record(reason);
        return VerifyResult.failure(VerifyOutcome.VERIFICATION_FAILED);
    }

    private void record(SecurityReason reason) {
        try {
            securityEventSink.record(new SecurityEvent(SecurityOperation.CHALLENGE_VERIFY, reason));
        } catch (RuntimeException ignored) {
            // Observability must not change credential-consumption semantics.
        }
    }
}
