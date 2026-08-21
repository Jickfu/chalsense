package io.github.chalsense.core.state.serialization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal strict JSON parser for bounded internal state; deliberately has no polymorphic mapping. */
final class StrictJsonParser {
    static final long MAXIMUM_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final int MAXIMUM_DEPTH = 16;

    private final String input;
    private int position;

    private StrictJsonParser(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        StrictJsonParser parser = new StrictJsonParser(input);
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.position != input.length()) {
            throw parser.error("trailing JSON content");
        }
        return value;
    }

    private Object readValue(int depth) {
        if (depth > MAXIMUM_DEPTH) {
            throw error("JSON nesting is too deep");
        }
        skipWhitespace();
        if (position >= input.length()) {
            throw error("unexpected end of JSON");
        }
        return switch (input.charAt(position)) {
            case '{' -> readObject(depth + 1);
            case '[' -> readArray(depth + 1);
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readInteger();
        };
    }

    private Map<String, Object> readObject(int depth) {
        position++;
        Map<String, Object> values = new LinkedHashMap<>();
        skipWhitespace();
        if (consume('}')) {
            return values;
        }
        while (true) {
            skipWhitespace();
            if (position >= input.length() || input.charAt(position) != '"') {
                throw error("object member name must be a string");
            }
            String name = readString();
            skipWhitespace();
            require(':');
            Object value = readValue(depth);
            if (values.containsKey(name)) {
                throw error("duplicate object member: " + name);
            }
            values.put(name, value);
            skipWhitespace();
            if (consume('}')) {
                return values;
            }
            require(',');
        }
    }

    private List<Object> readArray(int depth) {
        position++;
        List<Object> values = new ArrayList<>();
        skipWhitespace();
        if (consume(']')) {
            return values;
        }
        while (true) {
            values.add(readValue(depth));
            skipWhitespace();
            if (consume(']')) {
                return values;
            }
            require(',');
        }
    }

    private String readString() {
        require('"');
        StringBuilder value = new StringBuilder();
        while (position < input.length()) {
            char character = input.charAt(position++);
            if (character == '"') {
                return value.toString();
            }
            if (character < 0x20) {
                throw error("unescaped control character in string");
            }
            if (character == '\\') {
                value.append(readEscape());
            } else if (Character.isHighSurrogate(character)) {
                if (position >= input.length() || !Character.isLowSurrogate(input.charAt(position))) {
                    throw error("unpaired high surrogate");
                }
                value.append(character).append(input.charAt(position++));
            } else if (Character.isLowSurrogate(character)) {
                throw error("unpaired low surrogate");
            } else {
                value.append(character);
            }
        }
        throw error("unterminated string");
    }

    private String readEscape() {
        if (position >= input.length()) {
            throw error("unterminated escape sequence");
        }
        return switch (input.charAt(position++)) {
            case '"' -> "\"";
            case '\\' -> "\\";
            case '/' -> "/";
            case 'b' -> "\b";
            case 'f' -> "\f";
            case 'n' -> "\n";
            case 'r' -> "\r";
            case 't' -> "\t";
            case 'u' -> readUnicodeEscape();
            default -> throw error("invalid escape sequence");
        };
    }

    private String readUnicodeEscape() {
        char first = readHexCodeUnit();
        if (Character.isHighSurrogate(first)) {
            if (position + 1 >= input.length() || input.charAt(position) != '\\' || input.charAt(position + 1) != 'u') {
                throw error("unpaired escaped high surrogate");
            }
            position += 2;
            char second = readHexCodeUnit();
            if (!Character.isLowSurrogate(second)) {
                throw error("unpaired escaped high surrogate");
            }
            return new String(new char[]{first, second});
        }
        if (Character.isLowSurrogate(first)) {
            throw error("unpaired escaped low surrogate");
        }
        return String.valueOf(first);
    }

    private char readHexCodeUnit() {
        if (position + 4 > input.length()) {
            throw error("short unicode escape");
        }
        int value = 0;
        for (int index = 0; index < 4; index++) {
            int digit = asciiHexDigit(input.charAt(position++));
            if (digit < 0) {
                throw error("invalid unicode escape");
            }
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private Long readInteger() {
        int start = position;
        if (consume('-') && position >= input.length()) {
            throw error("incomplete number");
        }
        if (position >= input.length() || !isAsciiDigit(input.charAt(position))) {
            throw error("unexpected JSON token");
        }
        if (input.charAt(position) == '0') {
            position++;
            if (position < input.length() && isAsciiDigit(input.charAt(position))) {
                throw error("leading zero in number");
            }
        } else {
            while (position < input.length() && isAsciiDigit(input.charAt(position))) {
                position++;
            }
        }
        if (position < input.length()) {
            char suffix = input.charAt(position);
            if (suffix == '.' || suffix == 'e' || suffix == 'E' || suffix == '+') {
                throw error("state numbers must be integers without exponent notation");
            }
        }
        final long value;
        try {
            value = Long.parseLong(input.substring(start, position));
        } catch (NumberFormatException exception) {
            throw new StateSerializationException("integer is outside supported range", exception);
        }
        if (value < -MAXIMUM_SAFE_INTEGER || value > MAXIMUM_SAFE_INTEGER) {
            throw error("integer is outside JSON safe integer range");
        }
        return value;
    }

    private Object readLiteral(String literal, Object value) {
        if (!input.startsWith(literal, position)) {
            throw error("unexpected JSON token");
        }
        position += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (position < input.length()) {
            char character = input.charAt(position);
            if (character == ' ' || character == '\n' || character == '\r' || character == '\t') {
                position++;
            } else {
                return;
            }
        }
    }

    private boolean consume(char expected) {
        if (position < input.length() && input.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void require(char expected) {
        if (!consume(expected)) {
            throw error("expected '" + expected + "'");
        }
    }

    private StateSerializationException error(String message) {
        return new StateSerializationException(message + " at character " + position);
    }

    private static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static int asciiHexDigit(char character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'a' && character <= 'f') {
            return character - 'a' + 10;
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        return -1;
    }
}
