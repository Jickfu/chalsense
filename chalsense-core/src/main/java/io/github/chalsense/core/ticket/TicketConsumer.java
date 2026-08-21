package io.github.chalsense.core.ticket;

import io.github.chalsense.core.security.ConstantTime;
import io.github.chalsense.core.security.SecurityEvent;
import io.github.chalsense.core.security.SecurityEventSink;
import io.github.chalsense.core.security.SecurityOperation;
import io.github.chalsense.core.security.SecurityReason;
import io.github.chalsense.core.site.SiteAuthorizer;
import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.core.site.SiteRegistry;
import io.github.chalsense.core.state.StateStore;
import io.github.chalsense.core.state.TakeResult;
import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.core.state.TicketState;

import java.time.Clock;
import java.util.Objects;

/** Executes the approved one-time ticket consumption state machine. */
public final class TicketConsumer {
    private static final int CURRENT_STORAGE_VERSION = 1;

    private final StateStore stateStore;
    private final SiteRegistry siteRegistry;
    private final Clock clock;
    private final SecurityEventSink securityEventSink;

    public TicketConsumer(
            StateStore stateStore,
            SiteRegistry siteRegistry,
            Clock clock,
            SecurityEventSink securityEventSink) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.siteRegistry = Objects.requireNonNull(siteRegistry, "siteRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.securityEventSink = Objects.requireNonNull(securityEventSink, "securityEventSink");
    }

    public ConsumeResult consume(ConsumeTicketCommand command) {
        Objects.requireNonNull(command, "command");
        SiteRegistration registration = siteRegistry.find(command.siteKey()).orElse(null);
        if (registration == null || !SiteAuthorizer.allowsAction(registration, command.action())) {
            record(SecurityReason.CALLER_UNAUTHORIZED);
            return ConsumeResult.failure(ConsumeOutcome.CALLER_UNAUTHORIZED);
        }
        TakeResult<TicketState> takeResult = stateStore.takeTicket(TicketDigest.from(command.verificationTicket()));
        if (takeResult instanceof TakeResult.Absent<TicketState>) {
            return ConsumeResult.failure(ConsumeOutcome.TICKET_UNAVAILABLE);
        }
        if (takeResult instanceof TakeResult.Failed<TicketState>) {
            record(SecurityReason.STORE_FAILED);
            return ConsumeResult.failure(ConsumeOutcome.DEPENDENCY_UNAVAILABLE);
        }
        if (takeResult instanceof TakeResult.Unknown<TicketState>) {
            record(SecurityReason.STORE_RESULT_UNKNOWN);
            return ConsumeResult.failure(ConsumeOutcome.DEPENDENCY_UNAVAILABLE);
        }
        if (takeResult instanceof TakeResult.Unreadable<TicketState>) {
            record(SecurityReason.STORE_STATE_UNREADABLE);
            return ConsumeResult.failure(ConsumeOutcome.DEPENDENCY_UNAVAILABLE);
        }

        TicketState state = ((TakeResult.Present<TicketState>) takeResult).state();
        long now = clock.millis();
        if (now >= state.expiresAt()) {
            record(SecurityReason.EXPIRED);
            return ConsumeResult.failure(ConsumeOutcome.TICKET_UNAVAILABLE);
        }
        if (state.storageVersion() != CURRENT_STORAGE_VERSION) {
            record(SecurityReason.STORE_FAILED);
            return ConsumeResult.failure(ConsumeOutcome.DEPENDENCY_UNAVAILABLE);
        }
        if (state.protocolVersion() != command.protocolVersion()) {
            return ticketInvalid(SecurityReason.PROTOCOL_MISMATCH);
        }
        if (!ConstantTime.equalsUtf8(state.siteKey().value(), command.siteKey().value())) {
            return ticketInvalid(SecurityReason.SITE_MISMATCH);
        }
        if (!ConstantTime.equalsUtf8(state.action().value(), command.action().value())) {
            return ticketInvalid(SecurityReason.ACTION_MISMATCH);
        }
        if (!ConstantTime.equalsUtf8(state.contextDigest().value(), command.contextDigest().value())) {
            return ticketInvalid(SecurityReason.CONTEXT_MISMATCH);
        }
        return ConsumeResult.consumed(state.verifiedAt(), now);
    }

    private ConsumeResult ticketInvalid(SecurityReason reason) {
        record(reason);
        return ConsumeResult.failure(ConsumeOutcome.TICKET_INVALID);
    }

    private void record(SecurityReason reason) {
        try {
            securityEventSink.record(new SecurityEvent(SecurityOperation.TICKET_CONSUME, reason));
        } catch (RuntimeException ignored) {
            // Observability must not change credential-consumption semantics.
        }
    }
}
