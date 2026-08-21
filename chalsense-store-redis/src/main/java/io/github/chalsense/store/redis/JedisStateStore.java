package io.github.chalsense.store.redis;

import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.StateStore;
import io.github.chalsense.core.state.StoreChallengeResult;
import io.github.chalsense.core.state.StoreTicketResult;
import io.github.chalsense.core.state.TakeResult;
import io.github.chalsense.core.state.TicketDigest;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.core.state.serialization.StateJsonCodec;
import io.github.chalsense.core.state.serialization.StateSerializationException;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.SetParams;

import java.util.Objects;

/**
 * Jedis-backed StateStore for Redis OSS and Valkey.
 * The caller owns the RedisClient lifecycle; this store never closes it.
 */
public final class JedisStateStore implements StateStore {
    private static final String SET_CONFIRMED = "OK";

    private final BinaryRedisCommands commands;
    private final RedisKeyspace keyspace;
    private final StateJsonCodec stateJsonCodec;

    public JedisStateStore(RedisClient client, RedisKeyspace keyspace) {
        this(new JedisBinaryRedisCommands(client), keyspace, new StateJsonCodec());
    }

    JedisStateStore(
            BinaryRedisCommands commands,
            RedisKeyspace keyspace,
            StateJsonCodec stateJsonCodec) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.keyspace = Objects.requireNonNull(keyspace, "keyspace");
        this.stateJsonCodec = Objects.requireNonNull(stateJsonCodec, "stateJsonCodec");
    }

    @Override
    public StoreChallengeResult storeChallengeIfAbsent(
            SiteKey siteKey, ChallengeId challengeId, ChallengeState challengeState) {
        Objects.requireNonNull(challengeState, "challengeState");
        final byte[] encoded;
        try {
            encoded = stateJsonCodec.encodeChallenge(challengeState);
        } catch (StateSerializationException exception) {
            return StoreChallengeResult.FAILED;
        }
        try {
            String response = commands.set(
                    keyspace.challengeKey(siteKey, challengeId),
                    encoded,
                    SetParams.setParams().nx().pxAt(challengeState.expiresAt()));
            if (SET_CONFIRMED.equals(response)) {
                return StoreChallengeResult.CONFIRMED;
            }
            return response == null ? StoreChallengeResult.ALREADY_EXISTS : StoreChallengeResult.FAILED;
        } catch (JedisDataException exception) {
            return StoreChallengeResult.FAILED;
        } catch (JedisException exception) {
            return StoreChallengeResult.UNKNOWN;
        }
    }

    @Override
    public TakeResult<ChallengeState> takeChallenge(SiteKey siteKey, ChallengeId challengeId) {
        byte[] encoded;
        try {
            encoded = commands.getDel(keyspace.challengeKey(siteKey, challengeId));
        } catch (JedisDataException exception) {
            return new TakeResult.Failed<>();
        } catch (JedisException exception) {
            return new TakeResult.Unknown<>();
        }
        if (encoded == null) {
            return new TakeResult.Absent<>();
        }
        try {
            return new TakeResult.Present<>(stateJsonCodec.decodeChallenge(encoded));
        } catch (RuntimeException exception) {
            return new TakeResult.Unreadable<>();
        }
    }

    @Override
    public StoreTicketResult storeTicketIfAbsent(TicketDigest ticketDigest, TicketState ticketState) {
        Objects.requireNonNull(ticketState, "ticketState");
        final byte[] encoded;
        try {
            encoded = stateJsonCodec.encodeTicket(ticketState);
        } catch (StateSerializationException exception) {
            return StoreTicketResult.FAILED;
        }
        try {
            String response = commands.set(
                    keyspace.ticketKey(ticketDigest),
                    encoded,
                    SetParams.setParams().nx().pxAt(ticketState.expiresAt()));
            return SET_CONFIRMED.equals(response)
                    ? StoreTicketResult.CONFIRMED
                    : StoreTicketResult.FAILED;
        } catch (JedisDataException exception) {
            return StoreTicketResult.FAILED;
        } catch (JedisException exception) {
            return StoreTicketResult.UNKNOWN;
        }
    }

    @Override
    public TakeResult<TicketState> takeTicket(TicketDigest ticketDigest) {
        byte[] encoded;
        try {
            encoded = commands.getDel(keyspace.ticketKey(ticketDigest));
        } catch (JedisDataException exception) {
            return new TakeResult.Failed<>();
        } catch (JedisException exception) {
            return new TakeResult.Unknown<>();
        }
        if (encoded == null) {
            return new TakeResult.Absent<>();
        }
        try {
            return new TakeResult.Present<>(stateJsonCodec.decodeTicket(encoded));
        } catch (RuntimeException exception) {
            return new TakeResult.Unreadable<>();
        }
    }
}
