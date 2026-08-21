package io.github.chalsense.store.redis;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RedisStateStoreIntegrationTest {
    private static final SiteKey SITE_KEY = new SiteKey("site_test");
    private static final ChallengeId CHALLENGE_ID = new ChallengeId("AAAAAAAAAAAAAAAAAAAAAA");
    private static final TicketDigest TICKET_DIGEST = TicketDigest.from(new VerificationTicket(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));

    private static RedisClient client;
    private static RedisKeyspace keyspace;
    private static JedisStateStore firstStore;
    private static JedisStateStore secondStore;

    @BeforeAll
    static void connect() {
        assumeTrue(Boolean.getBoolean("chalsense.redis.integration"),
                "set -Dchalsense.redis.integration=true with a dedicated test server");
        String host = System.getProperty("chalsense.redis.host", "127.0.0.1");
        int port = Integer.getInteger("chalsense.redis.port", 6379);
        client = RedisClient.create(host, port);
        assertEquals("PONG", client.ping());
        keyspace = new RedisKeyspace("it" + UUID.randomUUID().toString().replace("-", ""));
        firstStore = new JedisStateStore(client, keyspace);
        secondStore = new JedisStateStore(client, keyspace);
    }

    @AfterAll
    static void close() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void sharesConfirmedChallengeAndTicketAcrossStoreInstances() {
        ChallengeState challenge = challengeState(System.currentTimeMillis() + 30_000);
        assertEquals(StoreChallengeResult.CONFIRMED,
                firstStore.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, challenge));
        assertEquals(StoreChallengeResult.ALREADY_EXISTS,
                secondStore.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, challenge));
        assertEquals(challenge, ((TakeResult.Present<ChallengeState>)
                secondStore.takeChallenge(SITE_KEY, CHALLENGE_ID)).state());
        assertInstanceOf(TakeResult.Absent.class, firstStore.takeChallenge(SITE_KEY, CHALLENGE_ID));

        TicketState ticket = ticketState(System.currentTimeMillis() + 30_000);
        assertEquals(StoreTicketResult.CONFIRMED, firstStore.storeTicketIfAbsent(TICKET_DIGEST, ticket));
        assertEquals(StoreTicketResult.FAILED, secondStore.storeTicketIfAbsent(TICKET_DIGEST, ticket));
        assertEquals(ticket, ((TakeResult.Present<TicketState>) secondStore.takeTicket(TICKET_DIGEST)).state());
        assertInstanceOf(TakeResult.Absent.class, firstStore.takeTicket(TICKET_DIGEST));
    }

    @Test
    void linearizesConcurrentCreateAndTake() throws Exception {
        ChallengeState state = challengeState(System.currentTimeMillis() + 30_000);
        List<StoreChallengeResult> stores = concurrently(
                () -> firstStore.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, state));
        assertEquals(1, stores.stream().filter(result -> result == StoreChallengeResult.CONFIRMED).count());
        assertEquals(31,
                stores.stream().filter(result -> result == StoreChallengeResult.ALREADY_EXISTS).count());

        List<TakeResult<ChallengeState>> takes = concurrently(
                () -> secondStore.takeChallenge(SITE_KEY, CHALLENGE_ID));
        assertEquals(1, takes.stream().filter(TakeResult.Present.class::isInstance).count());
        assertEquals(31, takes.stream().filter(TakeResult.Absent.class::isInstance).count());
    }

    @Test
    void usesExpiresAtAsRedisHardDeadline() throws Exception {
        long expiresAt = System.currentTimeMillis() + 1_000;
        ChallengeState state = challengeState(expiresAt);
        assertEquals(StoreChallengeResult.CONFIRMED,
                firstStore.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, state));
        byte[] key = keyspace.challengeKey(SITE_KEY, CHALLENGE_ID);
        long initialTtl = client.pttl(key);
        assertTrue(initialTtl > 0 && initialTtl <= 1_000, "PXAT must use the state's absolute deadline");

        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (client.exists(key) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertTrue(!client.exists(key), "Redis/Valkey must remove state by expiresAt");
        assertInstanceOf(TakeResult.Absent.class, firstStore.takeChallenge(SITE_KEY, CHALLENGE_ID));
    }

    @Test
    void consumesCorruptBytesAsUnreadableWithoutRestoringThem() {
        byte[] key = keyspace.challengeKey(SITE_KEY, CHALLENGE_ID);
        assertEquals("OK", client.set(
                key,
                "not-json".getBytes(StandardCharsets.UTF_8),
                SetParams.setParams().nx().pxAt(System.currentTimeMillis() + 30_000)));

        assertInstanceOf(TakeResult.Unreadable.class,
                firstStore.takeChallenge(SITE_KEY, CHALLENGE_ID));
        assertTrue(!client.exists(key));
        assertInstanceOf(TakeResult.Absent.class,
                secondStore.takeChallenge(SITE_KEY, CHALLENGE_ID));
    }

    @Test
    void mapsExplicitWrongTypeWithoutClaimingConsumption() {
        byte[] key = keyspace.challengeKey(SITE_KEY, CHALLENGE_ID);
        String keyText = new String(key, StandardCharsets.US_ASCII);
        client.hset(keyText, "field", "value");
        try {
            assertInstanceOf(TakeResult.Failed.class,
                    firstStore.takeChallenge(SITE_KEY, CHALLENGE_ID));
            assertTrue(client.exists(key), "a rejected GETDEL must not be reported as a consumed state");
        } finally {
            client.del(keyText);
        }
    }

    @Test
    void isolatesIdenticalLogicalKeysByNamespace() {
        RedisKeyspace otherKeyspace = new RedisKeyspace(keyspace.namespace() + ".other");
        JedisStateStore otherStore = new JedisStateStore(client, otherKeyspace);
        ChallengeState state = challengeState(System.currentTimeMillis() + 30_000);

        assertEquals(StoreChallengeResult.CONFIRMED,
                firstStore.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, state));
        assertEquals(StoreChallengeResult.CONFIRMED,
                otherStore.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, state));
        assertInstanceOf(TakeResult.Present.class,
                firstStore.takeChallenge(SITE_KEY, CHALLENGE_ID));
        assertInstanceOf(TakeResult.Present.class,
                otherStore.takeChallenge(SITE_KEY, CHALLENGE_ID));
    }

    private static <T> List<T> concurrently(ThrowingSupplier<T> operation) throws Exception {
        int concurrency = 32;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < concurrency; index++) {
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

    private static ChallengeState challengeState(long expiresAt) {
        long issuedAt = expiresAt - 30_000;
        return new ChallengeState(
                1, ProtocolVersion.V1, ChallengeType.SLIDER_PUZZLE, SITE_KEY, new ActionName("login"), context(),
                issuedAt, expiresAt,
                new SliderPuzzleGeometry(100_000, 700_000, 200_000, 150_000, 200_000, 20_000),
                "policy-v1");
    }

    private static TicketState ticketState(long expiresAt) {
        long issuedAt = expiresAt - 30_000;
        return new TicketState(
                1, ProtocolVersion.V1, SITE_KEY, new ActionName("login"), context(),
                ChallengeType.SLIDER_PUZZLE, "policy-v1", issuedAt, issuedAt, expiresAt);
    }

    private static ContextDigest context() {
        return new ContextDigest("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
