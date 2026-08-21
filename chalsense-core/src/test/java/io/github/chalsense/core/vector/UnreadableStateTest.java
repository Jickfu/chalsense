package io.github.chalsense.core.vector;

import io.github.chalsense.core.security.SecurityReason;
import io.github.chalsense.core.site.CallerContext;
import io.github.chalsense.core.site.SitePolicy;
import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.core.site.SiteRegistry;
import io.github.chalsense.core.site.SiteStatus;
import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.StateStore;
import io.github.chalsense.core.state.StoreChallengeResult;
import io.github.chalsense.core.state.StoreTicketResult;
import io.github.chalsense.core.state.TakeResult;
import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.core.ticket.ConsumeOutcome;
import io.github.chalsense.core.ticket.ConsumeTicketCommand;
import io.github.chalsense.core.ticket.TicketConsumer;
import io.github.chalsense.core.verify.ChallengeVerifier;
import io.github.chalsense.core.verify.VerifyChallengeCommand;
import io.github.chalsense.core.verify.VerifyOutcome;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.Track;
import io.github.chalsense.protocol.VerificationTicket;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnreadableStateTest {
    private static final SiteKey SITE_KEY = new SiteKey("site_test");
    private static final ActionName ACTION = new ActionName("login");
    private static final ChallengeId CHALLENGE_ID = new ChallengeId("AAAAAAAAAAAAAAAAAAAAAA");
    private static final VerificationTicket TICKET = new VerificationTicket(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    private static final ContextDigest CONTEXT = new ContextDigest(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

    @Test
    void verifyFailsClosedWhenTakenStateIsUnreadable() {
        TestSupport.CollectingSecurityEventSink events = new TestSupport.CollectingSecurityEventSink();
        ChallengeVerifier verifier = new ChallengeVerifier(
                unreadableStore(), registry(), fixedClock(),
                new TestSupport.FixedTokenGenerator(CHALLENGE_ID, TICKET), events);

        var result = verifier.verify(new VerifyChallengeCommand(
                ProtocolVersion.V1, SITE_KEY, CHALLENGE_ID, 0, new Track(List.of()),
                CallerContext.trustedBackend()));

        assertEquals(VerifyOutcome.DEPENDENCY_UNAVAILABLE, result.outcome());
        assertEquals(SecurityReason.STORE_STATE_UNREADABLE, events.events().get(0).reason());
    }

    @Test
    void consumeFailsClosedWhenTakenStateIsUnreadable() {
        TestSupport.CollectingSecurityEventSink events = new TestSupport.CollectingSecurityEventSink();
        TicketConsumer consumer = new TicketConsumer(unreadableStore(), registry(), fixedClock(), events);

        var result = consumer.consume(new ConsumeTicketCommand(
                ProtocolVersion.V1, TICKET, SITE_KEY, ACTION, CONTEXT));

        assertEquals(ConsumeOutcome.DEPENDENCY_UNAVAILABLE, result.outcome());
        assertEquals(SecurityReason.STORE_STATE_UNREADABLE, events.events().get(0).reason());
    }

    private static SiteRegistry registry() {
        SiteRegistration registration = new SiteRegistration(
                SITE_KEY,
                "Test site",
                SiteStatus.ACTIVE,
                SitePolicy.defaults("policy-v1", Set.of(ACTION), Set.of()));
        return requested -> requested.equals(SITE_KEY) ? Optional.of(registration) : Optional.empty();
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);
    }

    private static StateStore unreadableStore() {
        return new StateStore() {
            @Override
            public StoreChallengeResult storeChallengeIfAbsent(
                    SiteKey siteKey, ChallengeId challengeId, ChallengeState challengeState) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TakeResult<ChallengeState> takeChallenge(SiteKey siteKey, ChallengeId challengeId) {
                return new TakeResult.Unreadable<>();
            }

            @Override
            public StoreTicketResult storeTicketIfAbsent(TicketDigest ticketDigest, TicketState ticketState) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TakeResult<TicketState> takeTicket(TicketDigest ticketDigest) {
                return new TakeResult.Unreadable<>();
            }
        };
    }
}
