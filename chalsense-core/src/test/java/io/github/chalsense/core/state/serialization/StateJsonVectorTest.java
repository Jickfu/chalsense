package io.github.chalsense.core.state.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.SliderPuzzleGeometry;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeType;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StateJsonVectorTest {
    private static final StateJsonCodec CODEC = new StateJsonCodec();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static JsonNode root;
    private static JsonNode fixtures;

    @BeforeAll
    static void loadVectors() throws IOException {
        try (InputStream input = StateJsonVectorTest.class.getResourceAsStream("/state-json-v1.json")) {
            root = new ObjectMapper().readTree(input);
            fixtures = root.required("fixtures");
        }
        assertEquals("chalsense-state-json-v1", root.required("vectorSet").textValue());
        assertEquals("approved-v0.1", root.required("status").textValue());
    }

    @TestFactory
    Stream<DynamicTest> goldenVectors() {
        List<JsonNode> vectors = new ArrayList<>();
        root.required("goldenVectors").forEach(vectors::add);
        return vectors.stream().map(vector -> DynamicTest.dynamicTest(
                vector.required("id").textValue(), () -> executeGolden(vector)));
    }

    @TestFactory
    Stream<DynamicTest> readVectors() {
        List<JsonNode> vectors = new ArrayList<>();
        root.required("readVectors").forEach(vectors::add);
        return vectors.stream().map(vector -> DynamicTest.dynamicTest(
                vector.required("id").textValue(), () -> executeRead(vector)));
    }

    @Test
    void rejectsBomMalformedUtf8AndOversizedInput() {
        assertThrows(StateSerializationException.class,
                () -> CODEC.decodeChallenge(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '{', '}'}));
        assertThrows(StateSerializationException.class,
                () -> CODEC.decodeChallenge(new byte[]{(byte) 0xc3, (byte) 0x28}));
        assertThrows(StateSerializationException.class,
                () -> CODEC.decodeChallenge(new byte[StateJsonCodec.MAXIMUM_ENCODED_BYTES + 1]));
    }

    @Test
    void escapesStringsAndReadsEquivalentUnicodeEscapes() {
        ChallengeState state = challengeFixture();
        ChallengeState escaped = new ChallengeState(
                state.storageVersion(), state.protocolVersion(), state.challengeType(), state.siteKey(), state.action(),
                state.contextDigest(), state.issuedAt(), state.expiresAt(), state.geometry(), "policy-\"\\\n-测试");
        byte[] encoded = CODEC.encodeChallenge(escaped);
        assertEquals(escaped, CODEC.decodeChallenge(encoded));
        String unicodeEscaped = new String(encoded, StandardCharsets.UTF_8).replace("测试", "\\u6d4b\\u8bd5");
        assertEquals(escaped, CODEC.decodeChallenge(unicodeEscaped.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void writerRejectsIntegersOutsideJsonSafeRange() {
        TicketState state = ticketFixture();
        TicketState unsafe = new TicketState(
                state.storageVersion(), state.protocolVersion(), state.siteKey(), state.action(),
                state.contextDigest(), state.challengeType(), state.policyVersion(),
                state.verifiedAt(), state.issuedAt(), 9_007_199_254_740_992L);
        assertThrows(StateSerializationException.class, () -> CODEC.encodeTicket(unsafe));
    }

    private static void executeGolden(JsonNode vector) throws IOException {
        String kind = vector.required("kind").textValue();
        byte[] expected = vector.required("canonicalJson").textValue().getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, OBJECT_MAPPER.writeValueAsBytes(OBJECT_MAPPER.readTree(expected)));
        if (kind.equals("challenge")) {
            ChallengeState state = challengeFixture();
            assertArrayEquals(expected, CODEC.encodeChallenge(state));
            assertEquals(state, CODEC.decodeChallenge(expected));
        } else {
            TicketState state = ticketFixture();
            assertArrayEquals(expected, CODEC.encodeTicket(state));
            assertEquals(state, CODEC.decodeTicket(expected));
        }
    }

    private static void executeRead(JsonNode vector) {
        byte[] encoded = vector.required("json").textValue().getBytes(StandardCharsets.UTF_8);
        String kind = vector.required("kind").textValue();
        if (vector.required("expected").textValue().equals("REJECT")) {
            assertThrows(StateSerializationException.class, () -> decode(kind, encoded));
        } else {
            assertEquals(ticketFixture(), decode(kind, encoded));
        }
    }

    private static Object decode(String kind, byte[] encoded) {
        return kind.equals("challenge") ? CODEC.decodeChallenge(encoded) : CODEC.decodeTicket(encoded);
    }

    private static ChallengeState challengeFixture() {
        JsonNode state = fixtures.required("challenge");
        JsonNode geometry = state.required("geometry");
        return new ChallengeState(
                state.required("storageVersion").intValue(),
                ProtocolVersion.fromWireValue(state.required("protocolVersion").textValue()),
                ChallengeType.valueOf(state.required("challengeType").textValue()),
                new SiteKey(state.required("siteKey").textValue()),
                new ActionName(state.required("action").textValue()),
                new ContextDigest(state.required("contextDigest").textValue()),
                state.required("issuedAt").longValue(),
                state.required("expiresAt").longValue(),
                new SliderPuzzleGeometry(
                        geometry.required("pieceStartX").longValue(),
                        geometry.required("pieceTargetX").longValue(),
                        geometry.required("pieceStartY").longValue(),
                        geometry.required("pieceWidth").longValue(),
                        geometry.required("pieceHeight").longValue(),
                        geometry.required("tolerance").longValue()),
                state.required("policyVersion").textValue());
    }

    private static TicketState ticketFixture() {
        JsonNode state = fixtures.required("ticket");
        return new TicketState(
                state.required("storageVersion").intValue(),
                ProtocolVersion.fromWireValue(state.required("protocolVersion").textValue()),
                new SiteKey(state.required("siteKey").textValue()),
                new ActionName(state.required("action").textValue()),
                new ContextDigest(state.required("contextDigest").textValue()),
                ChallengeType.valueOf(state.required("challengeType").textValue()),
                state.required("policyVersion").textValue(),
                state.required("verifiedAt").longValue(),
                state.required("issuedAt").longValue(),
                state.required("expiresAt").longValue());
    }
}
