package io.github.chalsense.core.state;

import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;

/**
 * Atomic state boundary shared by embedded and service deployments.
 * Implementations must linearize each method across processes and return Unknown when the caller
 * cannot determine whether a mutating operation took effect. Production stores also use
 * state.expiresAt as the hard TTL; deterministic test stores may use an injected lifecycle.
 */
public interface StateStore {
    /** Stores a challenge only when the (siteKey, challengeId) key is absent. */
    StoreChallengeResult storeChallengeIfAbsent(
            SiteKey siteKey, ChallengeId challengeId, ChallengeState challengeState);

    /** Atomically reads and deletes at most one challenge; taken state must never be restored. */
    TakeResult<ChallengeState> takeChallenge(SiteKey siteKey, ChallengeId challengeId);

    /** Stores a ticket only when its digest is absent; never overwrites an existing ticket. */
    StoreTicketResult storeTicketIfAbsent(TicketDigest ticketDigest, TicketState ticketState);

    /** Atomically reads and deletes at most one ticket; taken state must never be restored. */
    TakeResult<TicketState> takeTicket(TicketDigest ticketDigest);
}
