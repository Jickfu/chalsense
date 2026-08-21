package io.github.chalsense.core.vector;

import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.SliderPuzzleGeometry;
import io.github.chalsense.core.state.StoreChallengeResult;
import io.github.chalsense.core.state.StoreTicketResult;
import io.github.chalsense.core.state.TakeResult;
import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ChallengeType;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.VerificationTicket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreContractTest {
    private static final int CONCURRENCY = 32;
    private static final SiteKey SITE_KEY = new SiteKey("site_test");
    private static final ChallengeId CHALLENGE_ID = new ChallengeId("AAAAAAAAAAAAAAAAAAAAAA");
    private static final ContextDigest CONTEXT = new ContextDigest(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

    @Test
    void challengeCreateIfAbsentHasOneWinnerAndNeverOverwrites() throws Exception {
        TestStateStore store = new TestStateStore();
        List<StoreChallengeResult> results = concurrently(
                () -> store.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, challengeState()));

        assertEquals(1, results.stream().filter(result -> result == StoreChallengeResult.CONFIRMED).count());
        assertEquals(CONCURRENCY - 1,
                results.stream().filter(result -> result == StoreChallengeResult.ALREADY_EXISTS).count());
        assertEquals(challengeState(), store.challengeState(SITE_KEY, CHALLENGE_ID).orElseThrow());
    }

    @Test
    void challengeTakeIsAtomicReadAndDelete() throws Exception {
        TestStateStore store = new TestStateStore();
        store.seedChallenge(SITE_KEY, CHALLENGE_ID, challengeState());
        List<TakeResult<ChallengeState>> results = concurrently(
                () -> store.takeChallenge(SITE_KEY, CHALLENGE_ID));

        assertEquals(1, results.stream().filter(TakeResult.Present.class::isInstance).count());
        assertEquals(CONCURRENCY - 1, results.stream().filter(TakeResult.Absent.class::isInstance).count());
        assertTrue(!store.hasChallenge(SITE_KEY, CHALLENGE_ID));
    }

    @Test
    void ticketCreateIfAbsentAndTakeEachHaveOneWinner() throws Exception {
        TestStateStore store = new TestStateStore();
        TicketDigest digest = TicketDigest.from(new VerificationTicket(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        List<StoreTicketResult> stores = concurrently(() -> store.storeTicketIfAbsent(digest, ticketState()));
        assertEquals(1, stores.stream().filter(result -> result == StoreTicketResult.CONFIRMED).count());

        List<TakeResult<TicketState>> takes = concurrently(() -> store.takeTicket(digest));
        assertEquals(1, takes.stream().filter(TakeResult.Present.class::isInstance).count());
        assertEquals(CONCURRENCY - 1, takes.stream().filter(TakeResult.Absent.class::isInstance).count());
    }

    @Test
    void confirmedPreExecutionFailureDoesNotDeleteState() {
        TestStateStore store = new TestStateStore();
        store.seedChallenge(SITE_KEY, CHALLENGE_ID, challengeState());
        store.challengeTakeMode(TestStateStore.TakeMode.FAILED);

        assertInstanceOf(TakeResult.Failed.class, store.takeChallenge(SITE_KEY, CHALLENGE_ID));
        assertTrue(store.hasChallenge(SITE_KEY, CHALLENGE_ID));
    }

    private static <T> List<T> concurrently(ThrowingSupplier<T> operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < CONCURRENCY; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return operation.get();
                }));
            }
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static ChallengeState challengeState() {
        return new ChallengeState(
                1, ProtocolVersion.V1, ChallengeType.SLIDER_PUZZLE, SITE_KEY, new ActionName("login"), CONTEXT,
                1_000, 121_000,
                new SliderPuzzleGeometry(100_000, 700_000, 200_000, 150_000, 200_000, 20_000),
                "policy-v1");
    }

    private static TicketState ticketState() {
        return new TicketState(
                1, ProtocolVersion.V1, SITE_KEY, new ActionName("login"), CONTEXT,
                ChallengeType.SLIDER_PUZZLE, "policy-v1", 2_000, 2_000, 62_000);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
