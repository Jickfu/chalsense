package io.github.chalsense.store.redis;

import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.SliderPuzzleGeometry;
import io.github.chalsense.core.state.StoreChallengeResult;
import io.github.chalsense.core.state.StoreTicketResult;
import io.github.chalsense.core.state.TakeResult;
import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.core.state.serialization.StateJsonCodec;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.ChallengeType;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import io.github.chalsense.protocol.VerificationTicket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.RedisClusterClient;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.SetParams;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JedisStateStoreTest {
    private static final SiteKey SITE_KEY = new SiteKey("site_test");
    private static final ChallengeId CHALLENGE_ID = new ChallengeId("AAAAAAAAAAAAAAAAAAAAAA");
    private static final VerificationTicket TICKET = new VerificationTicket(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    private static final TicketDigest TICKET_DIGEST = TicketDigest.from(TICKET);

    private FakeCommands commands;
    private RedisKeyspace keyspace;
    private StateJsonCodec codec;
    private JedisStateStore store;

    @BeforeEach
    void setUp() {
        commands = new FakeCommands();
        keyspace = new RedisKeyspace("test");
        codec = new StateJsonCodec();
        store = new JedisStateStore(commands, keyspace, codec);
    }

    @Test
    void storesChallengeAsFrozenBytesWithNxAndAbsoluteExpiry() {
        commands.setResponse = "OK";
        ChallengeState state = challengeState();

        assertEquals(StoreChallengeResult.CONFIRMED,
                store.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, state));
        assertArrayEquals(keyspace.challengeKey(SITE_KEY, CHALLENGE_ID), commands.lastKey);
        assertArrayEquals(codec.encodeChallenge(state), commands.lastValue);
        assertEquals(SetParams.setParams().nx().pxAt(state.expiresAt()), commands.lastParams);
    }

    @Test
    void mapsConfirmedChallengeCollisionWithoutOverwrite() {
        commands.setResponse = null;
        assertEquals(StoreChallengeResult.ALREADY_EXISTS,
                store.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, challengeState()));
    }

    @Test
    void storesAndReadsTicketUsingDigestKey() {
        commands.setResponse = "OK";
        TicketState state = ticketState();
        assertEquals(StoreTicketResult.CONFIRMED, store.storeTicketIfAbsent(TICKET_DIGEST, state));
        assertArrayEquals(keyspace.ticketKey(TICKET_DIGEST), commands.lastKey);
        assertArrayEquals(codec.encodeTicket(state), commands.lastValue);
        assertEquals(SetParams.setParams().nx().pxAt(state.expiresAt()), commands.lastParams);

        commands.getDelResponse = codec.encodeTicket(state);
        TakeResult<TicketState> result = store.takeTicket(TICKET_DIGEST);
        assertEquals(state, ((TakeResult.Present<TicketState>) result).state());
    }

    @Test
    void decodesTakenChallengeAndMapsMissingState() {
        ChallengeState state = challengeState();
        commands.getDelResponse = codec.encodeChallenge(state);
        assertEquals(state, ((TakeResult.Present<ChallengeState>)
                store.takeChallenge(SITE_KEY, CHALLENGE_ID)).state());

        commands.getDelResponse = null;
        assertInstanceOf(TakeResult.Absent.class, store.takeChallenge(SITE_KEY, CHALLENGE_ID));
    }

    @Test
    void mapsTakenInvalidBytesToUnreadable() {
        commands.getDelResponse = "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertInstanceOf(TakeResult.Unreadable.class, store.takeChallenge(SITE_KEY, CHALLENGE_ID));
        assertInstanceOf(TakeResult.Unreadable.class, store.takeTicket(TICKET_DIGEST));
    }

    @Test
    void mapsExplicitServerErrorsToFailed() {
        commands.setException = new JedisDataException("WRONGTYPE");
        assertEquals(StoreChallengeResult.FAILED,
                store.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, challengeState()));
        assertEquals(StoreTicketResult.FAILED, store.storeTicketIfAbsent(TICKET_DIGEST, ticketState()));

        commands.setException = null;
        commands.getDelException = new JedisDataException("WRONGTYPE");
        assertInstanceOf(TakeResult.Failed.class, store.takeChallenge(SITE_KEY, CHALLENGE_ID));
        assertInstanceOf(TakeResult.Failed.class, store.takeTicket(TICKET_DIGEST));
    }

    @Test
    void mapsConnectionFailuresToUnknownWithoutRetry() {
        commands.setException = new JedisConnectionException("connection lost");
        assertEquals(StoreChallengeResult.UNKNOWN,
                store.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, challengeState()));
        assertEquals(1, commands.setCalls);

        commands.setException = null;
        commands.getDelException = new JedisConnectionException("response lost");
        assertInstanceOf(TakeResult.Unknown.class, store.takeChallenge(SITE_KEY, CHALLENGE_ID));
        assertEquals(1, commands.getDelCalls);
    }

    @Test
    void rejectsUnsupportedStateBeforeSendingCommand() {
        ChallengeState unsupported = new ChallengeState(
                2, ProtocolVersion.V1, ChallengeType.SLIDER_PUZZLE, SITE_KEY, new ActionName("login"), context(),
                1_000, 121_000, geometry(), "policy-v1");
        assertEquals(StoreChallengeResult.FAILED,
                store.storeChallengeIfAbsent(SITE_KEY, CHALLENGE_ID, unsupported));
        assertEquals(0, commands.setCalls);
    }

    @Test
    void exposesStandaloneClientButNotRetryingClusterClient() {
        assertTrue(java.util.Arrays.stream(JedisStateStore.class.getConstructors())
                .anyMatch(constructor -> java.util.Arrays.asList(constructor.getParameterTypes())
                        .contains(RedisClient.class)));
        assertFalse(java.util.Arrays.stream(JedisStateStore.class.getConstructors())
                .anyMatch(constructor -> java.util.Arrays.asList(constructor.getParameterTypes())
                        .contains(RedisClusterClient.class)));
    }

    private static ChallengeState challengeState() {
        return new ChallengeState(
                1, ProtocolVersion.V1, ChallengeType.SLIDER_PUZZLE, SITE_KEY, new ActionName("login"), context(),
                1_000, 121_000, geometry(), "policy-v1");
    }

    private static TicketState ticketState() {
        return new TicketState(
                1, ProtocolVersion.V1, SITE_KEY, new ActionName("login"), context(),
                ChallengeType.SLIDER_PUZZLE, "policy-v1", 2_000, 2_000, 62_000);
    }

    private static SliderPuzzleGeometry geometry() {
        return new SliderPuzzleGeometry(100_000, 700_000, 200_000, 150_000, 200_000, 20_000);
    }

    private static ContextDigest context() {
        return new ContextDigest("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    }

    private static final class FakeCommands implements BinaryRedisCommands {
        private String setResponse;
        private RuntimeException setException;
        private byte[] getDelResponse;
        private RuntimeException getDelException;
        private byte[] lastKey;
        private byte[] lastValue;
        private SetParams lastParams;
        private int setCalls;
        private int getDelCalls;

        @Override
        public String set(byte[] key, byte[] value, SetParams params) {
            setCalls++;
            lastKey = key;
            lastValue = value;
            lastParams = params;
            if (setException != null) {
                throw setException;
            }
            return setResponse;
        }

        @Override
        public byte[] getDel(byte[] key) {
            getDelCalls++;
            lastKey = key;
            if (getDelException != null) {
                throw getDelException;
            }
            return getDelResponse;
        }
    }
}
