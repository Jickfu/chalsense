package io.github.chalsense.core.vector;

import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.StateStore;
import io.github.chalsense.core.state.StoreChallengeResult;
import io.github.chalsense.core.state.StoreTicketResult;
import io.github.chalsense.core.state.TakeResult;
import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class TestStateStore implements StateStore {
    enum TakeMode {
        NORMAL,
        FAILED,
        UNKNOWN
    }

    private final Map<ChallengeKey, ChallengeState> challenges = new ConcurrentHashMap<>();
    private final Map<TicketDigest, TicketState> tickets = new ConcurrentHashMap<>();
    private final AtomicInteger challengeTakeCalls = new AtomicInteger();
    private final AtomicInteger challengeStoreCalls = new AtomicInteger();
    private final AtomicInteger challengeStoreSuccesses = new AtomicInteger();
    private final AtomicInteger challengeTakeSuccesses = new AtomicInteger();
    private final AtomicInteger ticketTakeCalls = new AtomicInteger();
    private final AtomicInteger ticketTakeSuccesses = new AtomicInteger();
    private final AtomicInteger ticketStoreSuccesses = new AtomicInteger();

    private volatile TakeMode challengeTakeMode = TakeMode.NORMAL;
    private volatile StoreChallengeResult challengeStoreResult = StoreChallengeResult.CONFIRMED;
    private volatile TakeMode ticketTakeMode = TakeMode.NORMAL;
    private volatile StoreTicketResult ticketStoreResult = StoreTicketResult.CONFIRMED;

    void seedChallenge(SiteKey siteKey, ChallengeId challengeId, ChallengeState state) {
        challenges.put(new ChallengeKey(siteKey, challengeId), state);
    }

    void seedTicket(TicketDigest digest, TicketState state) {
        tickets.put(digest, state);
    }

    void challengeTakeMode(TakeMode mode) {
        challengeTakeMode = mode;
    }

    void challengeStoreResult(StoreChallengeResult result) {
        challengeStoreResult = result;
    }

    void ticketTakeMode(TakeMode mode) {
        ticketTakeMode = mode;
    }

    void ticketStoreResult(StoreTicketResult result) {
        ticketStoreResult = result;
    }

    boolean hasChallenge(SiteKey siteKey, ChallengeId challengeId) {
        return challenges.containsKey(new ChallengeKey(siteKey, challengeId));
    }

    Optional<ChallengeState> challengeState(SiteKey siteKey, ChallengeId challengeId) {
        return Optional.ofNullable(challenges.get(new ChallengeKey(siteKey, challengeId)));
    }

    boolean hasTicket(TicketDigest digest) {
        return tickets.containsKey(digest);
    }

    int challengeTakeCalls() {
        return challengeTakeCalls.get();
    }

    int challengeTakeSuccesses() {
        return challengeTakeSuccesses.get();
    }

    int ticketTakeCalls() {
        return ticketTakeCalls.get();
    }

    int ticketTakeSuccesses() {
        return ticketTakeSuccesses.get();
    }

    int ticketStoreSuccesses() {
        return ticketStoreSuccesses.get();
    }

    int challengeStoreCalls() {
        return challengeStoreCalls.get();
    }

    int challengeStoreSuccesses() {
        return challengeStoreSuccesses.get();
    }

    @Override
    public StoreChallengeResult storeChallengeIfAbsent(
            SiteKey siteKey, ChallengeId challengeId, ChallengeState challengeState) {
        challengeStoreCalls.incrementAndGet();
        if (challengeStoreResult != StoreChallengeResult.CONFIRMED) {
            return challengeStoreResult;
        }
        if (challenges.putIfAbsent(new ChallengeKey(siteKey, challengeId), challengeState) != null) {
            return StoreChallengeResult.ALREADY_EXISTS;
        }
        challengeStoreSuccesses.incrementAndGet();
        return StoreChallengeResult.CONFIRMED;
    }

    @Override
    public TakeResult<ChallengeState> takeChallenge(SiteKey siteKey, ChallengeId challengeId) {
        challengeTakeCalls.incrementAndGet();
        if (challengeTakeMode == TakeMode.FAILED) {
            return new TakeResult.Failed<>();
        }
        if (challengeTakeMode == TakeMode.UNKNOWN) {
            return new TakeResult.Unknown<>();
        }
        ChallengeState state = challenges.remove(new ChallengeKey(siteKey, challengeId));
        if (state == null) {
            return new TakeResult.Absent<>();
        }
        challengeTakeSuccesses.incrementAndGet();
        return new TakeResult.Present<>(state);
    }

    @Override
    public StoreTicketResult storeTicketIfAbsent(TicketDigest ticketDigest, TicketState ticketState) {
        if (ticketStoreResult != StoreTicketResult.CONFIRMED) {
            return ticketStoreResult;
        }
        if (tickets.putIfAbsent(ticketDigest, ticketState) != null) {
            return StoreTicketResult.FAILED;
        }
        ticketStoreSuccesses.incrementAndGet();
        return StoreTicketResult.CONFIRMED;
    }

    @Override
    public TakeResult<TicketState> takeTicket(TicketDigest ticketDigest) {
        ticketTakeCalls.incrementAndGet();
        if (ticketTakeMode == TakeMode.FAILED) {
            return new TakeResult.Failed<>();
        }
        if (ticketTakeMode == TakeMode.UNKNOWN) {
            return new TakeResult.Unknown<>();
        }
        TicketState state = tickets.remove(ticketDigest);
        if (state == null) {
            return new TakeResult.Absent<>();
        }
        ticketTakeSuccesses.incrementAndGet();
        return new TakeResult.Present<>(state);
    }

    private record ChallengeKey(SiteKey siteKey, ChallengeId challengeId) {
    }
}
