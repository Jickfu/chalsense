package io.github.chalsense.core.state.serialization;

import io.github.chalsense.core.state.ChallengeState;
import io.github.chalsense.core.state.SliderPuzzleGeometry;
import io.github.chalsense.core.state.TicketState;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeType;
import io.github.chalsense.protocol.ContextDigest;
import io.github.chalsense.protocol.ProtocolVersion;
import io.github.chalsense.protocol.SiteKey;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic storageVersion 1 UTF-8 JSON codec with strict schema validation. */
public final class StateJsonCodec {
    public static final int CURRENT_STORAGE_VERSION = 1;
    public static final int MAXIMUM_ENCODED_BYTES = 16_384;

    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "storageVersion", "kind", "protocolVersion", "payload");
    private static final Set<String> CHALLENGE_FIELDS = Set.of(
            "challengeType", "siteKey", "action", "contextDigest", "issuedAt", "expiresAt",
            "geometry", "policyVersion");
    private static final Set<String> GEOMETRY_FIELDS = Set.of(
            "pieceStartX", "pieceTargetX", "pieceStartY", "pieceWidth", "pieceHeight", "tolerance");
    private static final Set<String> TICKET_FIELDS = Set.of(
            "siteKey", "action", "contextDigest", "challengeType", "policyVersion",
            "verifiedAt", "issuedAt", "expiresAt");

    public byte[] encodeChallenge(ChallengeState state) {
        Objects.requireNonNull(state, "state");
        requireCurrentVersion(state.storageVersion());
        SliderPuzzleGeometry geometry = state.geometry();
        StringBuilder json = envelopePrefix(state.protocolVersion(), "challenge");
        appendFirstName(json, "challengeType");
        appendString(json, state.challengeType().name());
        appendName(json, "siteKey");
        appendString(json, state.siteKey().value());
        appendName(json, "action");
        appendString(json, state.action().value());
        appendName(json, "contextDigest");
        appendString(json, state.contextDigest().value());
        appendName(json, "issuedAt");
        appendSafeInteger(json, state.issuedAt());
        appendName(json, "expiresAt");
        appendSafeInteger(json, state.expiresAt());
        appendName(json, "geometry");
        json.append('{');
        appendFirstName(json, "pieceStartX");
        appendSafeInteger(json, geometry.pieceStartX());
        appendName(json, "pieceTargetX");
        appendSafeInteger(json, geometry.pieceTargetX());
        appendName(json, "pieceStartY");
        appendSafeInteger(json, geometry.pieceStartY());
        appendName(json, "pieceWidth");
        appendSafeInteger(json, geometry.pieceWidth());
        appendName(json, "pieceHeight");
        appendSafeInteger(json, geometry.pieceHeight());
        appendName(json, "tolerance");
        appendSafeInteger(json, geometry.tolerance());
        json.append('}');
        appendName(json, "policyVersion");
        appendString(json, state.policyVersion());
        json.append("}}");
        return boundedUtf8(json);
    }

    public ChallengeState decodeChallenge(byte[] encoded) {
        Envelope envelope = decodeEnvelope(encoded, "challenge");
        Map<String, Object> payload = envelope.payload();
        requireExactFields(payload, CHALLENGE_FIELDS, "challenge payload");
        Map<String, Object> geometry = requiredObject(payload, "geometry");
        requireExactFields(geometry, GEOMETRY_FIELDS, "challenge geometry");
        try {
            return new ChallengeState(
                    CURRENT_STORAGE_VERSION,
                    envelope.protocolVersion(),
                    ChallengeType.valueOf(requiredString(payload, "challengeType")),
                    new SiteKey(requiredString(payload, "siteKey")),
                    new ActionName(requiredString(payload, "action")),
                    new ContextDigest(requiredString(payload, "contextDigest")),
                    requiredLong(payload, "issuedAt"),
                    requiredLong(payload, "expiresAt"),
                    new SliderPuzzleGeometry(
                            requiredLong(geometry, "pieceStartX"),
                            requiredLong(geometry, "pieceTargetX"),
                            requiredLong(geometry, "pieceStartY"),
                            requiredLong(geometry, "pieceWidth"),
                            requiredLong(geometry, "pieceHeight"),
                            requiredLong(geometry, "tolerance")),
                    requiredString(payload, "policyVersion"));
        } catch (StateSerializationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new StateSerializationException("invalid challenge state value", exception);
        }
    }

    public byte[] encodeTicket(TicketState state) {
        Objects.requireNonNull(state, "state");
        requireCurrentVersion(state.storageVersion());
        StringBuilder json = envelopePrefix(state.protocolVersion(), "ticket");
        appendFirstName(json, "siteKey");
        appendString(json, state.siteKey().value());
        appendName(json, "action");
        appendString(json, state.action().value());
        appendName(json, "contextDigest");
        appendString(json, state.contextDigest().value());
        appendName(json, "challengeType");
        appendString(json, state.challengeType().name());
        appendName(json, "policyVersion");
        appendString(json, state.policyVersion());
        appendName(json, "verifiedAt");
        appendSafeInteger(json, state.verifiedAt());
        appendName(json, "issuedAt");
        appendSafeInteger(json, state.issuedAt());
        appendName(json, "expiresAt");
        appendSafeInteger(json, state.expiresAt());
        json.append("}}");
        return boundedUtf8(json);
    }

    public TicketState decodeTicket(byte[] encoded) {
        Envelope envelope = decodeEnvelope(encoded, "ticket");
        Map<String, Object> payload = envelope.payload();
        requireExactFields(payload, TICKET_FIELDS, "ticket payload");
        try {
            return new TicketState(
                    CURRENT_STORAGE_VERSION,
                    envelope.protocolVersion(),
                    new SiteKey(requiredString(payload, "siteKey")),
                    new ActionName(requiredString(payload, "action")),
                    new ContextDigest(requiredString(payload, "contextDigest")),
                    ChallengeType.valueOf(requiredString(payload, "challengeType")),
                    requiredString(payload, "policyVersion"),
                    requiredLong(payload, "verifiedAt"),
                    requiredLong(payload, "issuedAt"),
                    requiredLong(payload, "expiresAt"));
        } catch (StateSerializationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new StateSerializationException("invalid ticket state value", exception);
        }
    }

    private static Envelope decodeEnvelope(byte[] encoded, String expectedKind) {
        String json = decodeUtf8(encoded);
        Object parsed = StrictJsonParser.parse(json);
        if (!(parsed instanceof Map<?, ?>)) {
            throw new StateSerializationException("state JSON must be an object");
        }
        Map<String, Object> envelope = castObject(parsed, "state envelope");
        requireExactFields(envelope, ENVELOPE_FIELDS, "state envelope");
        long storageVersion = requiredLong(envelope, "storageVersion");
        if (storageVersion != CURRENT_STORAGE_VERSION) {
            throw new StateSerializationException("unsupported storageVersion: " + storageVersion);
        }
        String kind = requiredString(envelope, "kind");
        if (!kind.equals(expectedKind)) {
            throw new StateSerializationException("unexpected state kind");
        }
        final ProtocolVersion protocolVersion;
        try {
            protocolVersion = ProtocolVersion.fromWireValue(requiredString(envelope, "protocolVersion"));
        } catch (IllegalArgumentException exception) {
            throw new StateSerializationException("unsupported protocolVersion", exception);
        }
        return new Envelope(protocolVersion, requiredObject(envelope, "payload"));
    }

    private static String decodeUtf8(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > MAXIMUM_ENCODED_BYTES) {
            throw new StateSerializationException("encoded state must contain 1..16384 bytes");
        }
        if (encoded.length >= 3
                && encoded[0] == (byte) 0xef && encoded[1] == (byte) 0xbb && encoded[2] == (byte) 0xbf) {
            throw new StateSerializationException("UTF-8 BOM is not allowed");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new StateSerializationException("state is not valid UTF-8", exception);
        }
    }

    private static byte[] boundedUtf8(StringBuilder json) {
        byte[] encoded = json.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAXIMUM_ENCODED_BYTES) {
            throw new StateSerializationException("encoded state exceeds 16384 bytes");
        }
        return encoded;
    }

    private static StringBuilder envelopePrefix(ProtocolVersion protocolVersion, String kind) {
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        appendFirstName(json, "storageVersion");
        json.append(CURRENT_STORAGE_VERSION);
        appendName(json, "kind");
        appendString(json, kind);
        appendName(json, "protocolVersion");
        appendString(json, protocolVersion.wireValue());
        appendName(json, "payload");
        json.append('{');
        return json;
    }

    private static void requireCurrentVersion(int storageVersion) {
        if (storageVersion != CURRENT_STORAGE_VERSION) {
            throw new StateSerializationException("writer only supports current storageVersion");
        }
    }

    private static void requireExactFields(Map<String, Object> object, Set<String> expected, String label) {
        if (!object.keySet().equals(expected)) {
            throw new StateSerializationException(label + " has missing or unknown fields");
        }
    }

    private static Map<String, Object> requiredObject(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (!(value instanceof Map<?, ?>)) {
            throw new StateSerializationException(name + " must be an object");
        }
        return castObject(value, name);
    }

    private static Map<String, Object> castObject(Object value, String label) {
        Map<?, ?> untyped = (Map<?, ?>) value;
        for (Object key : untyped.keySet()) {
            if (!(key instanceof String)) {
                throw new StateSerializationException(label + " has a non-string member name");
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) untyped;
        return typed;
    }

    private static String requiredString(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (!(value instanceof String text)) {
            throw new StateSerializationException(name + " must be a string");
        }
        return text;
    }

    private static long requiredLong(Map<String, Object> object, String name) {
        Object value = object.get(name);
        if (!(value instanceof Long number)) {
            throw new StateSerializationException(name + " must be an integer");
        }
        return number;
    }

    private static void appendFirstName(StringBuilder json, String name) {
        appendString(json, name);
        json.append(':');
    }

    private static void appendName(StringBuilder json, String name) {
        json.append(',');
        appendFirstName(json, name);
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append("\\u");
                        String hex = Integer.toHexString(character);
                        json.append("0".repeat(4 - hex.length())).append(hex);
                    } else if (Character.isHighSurrogate(character)) {
                        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                            throw new StateSerializationException("string contains an unpaired surrogate");
                        }
                        json.append(character).append(value.charAt(++index));
                    } else if (Character.isLowSurrogate(character)) {
                        throw new StateSerializationException("string contains an unpaired surrogate");
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static void appendSafeInteger(StringBuilder json, long value) {
        if (value < -StrictJsonParser.MAXIMUM_SAFE_INTEGER
                || value > StrictJsonParser.MAXIMUM_SAFE_INTEGER) {
            throw new StateSerializationException("state integer is outside JSON safe integer range");
        }
        json.append(value);
    }

    private record Envelope(ProtocolVersion protocolVersion, Map<String, Object> payload) {
    }
}
