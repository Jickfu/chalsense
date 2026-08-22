package io.github.chalsense.store.redis;

import io.github.chalsense.core.challenge.ChallengeResource;
import io.github.chalsense.core.challenge.ChallengeResourceRole;
import io.github.chalsense.core.challenge.resource.ChallengeBinaryResource;
import io.github.chalsense.core.challenge.resource.ChallengeResourceBundle;
import io.github.chalsense.core.challenge.resource.ChallengeResourceReadResult;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedisChallengeResourceStoreTest {
    @Test
    void publishesBothResourcesAtomicallyAndReadsRepeatedly() {
        FakeCommands commands = new FakeCommands();
        RedisChallengeResourceStore store = store(commands);

        List<ChallengeResource> resources = store.publish(bundle());

        assertEquals(1, commands.evalCalls);
        assertEquals("/v1/public/resources/AAAAAAAAAAAAAAAAAAAAAA/background", resources.get(0).url());
        commands.values = List.of(new byte[]{1, 2, 3}, "2000".getBytes());
        assertInstanceOf(ChallengeResourceReadResult.Found.class,
                store.read("AAAAAAAAAAAAAAAAAAAAAA", ChallengeResourceRole.BACKGROUND, 1000));
        assertInstanceOf(ChallengeResourceReadResult.Found.class,
                store.read("AAAAAAAAAAAAAAAAAAAAAA", ChallengeResourceRole.BACKGROUND, 1000));
    }

    @Test
    void retriesOnlyConfirmedIdentifierCollisions() {
        FakeCommands commands = new FakeCommands();
        commands.evalResults.add(0L);
        commands.evalResults.add(1L);
        RedisChallengeResourceStore store = store(commands);

        store.publish(bundle());

        assertEquals(2, commands.evalCalls);
    }

    @Test
    void mergesMissingExpiredMalformedAndOversizedReadsAsUnavailable() {
        FakeCommands commands = new FakeCommands();
        RedisChallengeResourceStore store = store(commands);
        assertInstanceOf(ChallengeResourceReadResult.Unavailable.class,
                store.read("bad", ChallengeResourceRole.PIECE, 0));
        commands.values = Arrays.asList(null, null);
        assertInstanceOf(ChallengeResourceReadResult.Unavailable.class,
                store.read("AAAAAAAAAAAAAAAAAAAAAA", ChallengeResourceRole.PIECE, 0));
        commands.values = List.of(new byte[]{1}, "1000".getBytes());
        assertInstanceOf(ChallengeResourceReadResult.Unavailable.class,
                store.read("AAAAAAAAAAAAAAAAAAAAAA", ChallengeResourceRole.PIECE, 1000));
        commands.values = List.of(new byte[256 * 1024 + 1], "2000".getBytes());
        assertInstanceOf(ChallengeResourceReadResult.Unavailable.class,
                store.read("AAAAAAAAAAAAAAAAAAAAAA", ChallengeResourceRole.PIECE, 1000));
    }

    @Test
    void deletesOnlyExactReferencesOwnedByThisPublisher() {
        FakeCommands commands = new FakeCommands();
        RedisChallengeResourceStore store = store(commands);
        List<ChallengeResource> resources = store.publish(bundle());
        store.delete(resources);
        assertEquals(1, commands.delCalls);

        List<ChallengeResource> foreign = resources.stream()
                .map(resource -> new ChallengeResource(resource.role(), "/foreign" + resource.url(),
                        resource.mediaType(), resource.pixelWidth(), resource.pixelHeight())).toList();
        assertThrows(IllegalArgumentException.class, () -> store.delete(foreign));
        assertEquals(1, commands.delCalls);
    }

    private static RedisChallengeResourceStore store(FakeCommands commands) {
        return new RedisChallengeResourceStore(commands, new RedisKeyspace(), new ZeroRandom(),
                "/v1/public/resources");
    }

    private static ChallengeResourceBundle bundle() {
        return new ChallengeResourceBundle(new SiteKey("site_test"),
                new ChallengeId("AAAAAAAAAAAAAAAAAAAAAA"), 2000, List.of(
                new ChallengeBinaryResource(ChallengeResourceRole.BACKGROUND, "image/png", 320, 180, new byte[]{1}),
                new ChallengeBinaryResource(ChallengeResourceRole.PIECE, "image/png", 32, 32, new byte[]{2})));
    }

    private static final class ZeroRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static final class FakeCommands implements ResourceRedisCommands {
        private final List<Object> evalResults = new ArrayList<>();
        private List<byte[]> values = Arrays.asList(null, null);
        private int evalCalls;
        private int delCalls;

        @Override
        public Object eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
            int index = evalCalls++;
            return index < evalResults.size() ? evalResults.get(index) : 1L;
        }

        @Override
        public List<byte[]> hmget(byte[] key, byte[]... fields) {
            return values;
        }

        @Override
        public long del(byte[] key) {
            delCalls++;
            return 1;
        }
    }
}
