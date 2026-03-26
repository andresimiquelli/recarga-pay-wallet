package recargapay.wallet.adapter.in.api.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class CanonicalRequestHasher {
    public String hash(String method, String requestUri, String queryString, byte[] body) {
        String canonicalRequest = method
                + "\n"
                + requestUri
                + "\n"
                + canonicalizeQuery(queryString)
                + "\n"
                + canonicalizeBody(body);
        return sha256(canonicalRequest);
    }

    private String canonicalizeQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }

        List<String> pairs = new ArrayList<>();
        for (String part : queryString.split("&")) {
            if (!part.isBlank()) {
                pairs.add(part);
            }
        }
        Collections.sort(pairs);
        return String.join("&", pairs);
    }

    private String canonicalizeBody(byte[] body) {
        String rawBody = new String(body, StandardCharsets.UTF_8).trim();
        if (rawBody.isEmpty()) {
            return "";
        }

        try {
            JsonValue jsonValue = new JsonParser(rawBody).parse();
            return jsonValue.render();
        } catch (RuntimeException exception) {
            return rawBody;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

    private sealed interface JsonValue permits JsonObjectValue, JsonArrayValue, JsonStringValue, JsonLiteralValue {
        String render();
    }

    private record JsonObjectValue(Map<String, JsonValue> values) implements JsonValue {
        @Override
        public String render() {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, JsonValue> entry : values.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(escape(entry.getKey())).append(':').append(entry.getValue().render());
            }
            return builder.append('}').toString();
        }
    }

    private record JsonArrayValue(List<JsonValue> values) implements JsonValue {
        @Override
        public String render() {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(values.get(index).render());
            }
            return builder.append(']').toString();
        }
    }

    private record JsonStringValue(String value) implements JsonValue {
        @Override
        public String render() {
            return escape(value);
        }
    }

    private record JsonLiteralValue(String value) implements JsonValue {
        @Override
        public String render() {
            return value;
        }
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> builder.append("\\\\");
                case '\"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    private static final class JsonParser {
        private final String input;
        private int index;

        private JsonParser(String input) {
            this.input = input;
        }

        private JsonValue parse() {
            JsonValue value = parseValue();
            skipWhitespace();
            if (index != input.length()) {
                throw new IllegalArgumentException("Unexpected trailing content");
            }
            return value;
        }

        private JsonValue parseValue() {
            skipWhitespace();
            if (index >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }

            char character = input.charAt(index);
            return switch (character) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> new JsonStringValue(parseString());
                case 't' -> parseLiteral("true");
                case 'f' -> parseLiteral("false");
                case 'n' -> parseLiteral("null");
                default -> parseNumber();
            };
        }

        private JsonObjectValue parseObject() {
            expect('{');
            skipWhitespace();
            Map<String, JsonValue> values = new TreeMap<>();
            if (peek('}')) {
                expect('}');
                return new JsonObjectValue(values);
            }

            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                JsonValue value = parseValue();
                values.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return new JsonObjectValue(values);
                }
                expect(',');
                skipWhitespace();
            }
        }

        private JsonArrayValue parseArray() {
            expect('[');
            skipWhitespace();
            List<JsonValue> values = new ArrayList<>();
            if (peek(']')) {
                expect(']');
                return new JsonArrayValue(values);
            }

            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return new JsonArrayValue(values);
                }
                expect(',');
                skipWhitespace();
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < input.length()) {
                char character = input.charAt(index++);
                if (character == '"') {
                    return builder.toString();
                }
                if (character == '\\') {
                    if (index >= input.length()) {
                        throw new IllegalArgumentException("Invalid escape sequence");
                    }
                    char escaped = input.charAt(index++);
                    switch (escaped) {
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> {
                            if (index + 4 > input.length()) {
                                throw new IllegalArgumentException("Invalid unicode escape");
                            }
                            String hex = input.substring(index, index + 4);
                            builder.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape sequence");
                    }
                    continue;
                }
                builder.append(character);
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private JsonLiteralValue parseLiteral(String literal) {
            if (!input.startsWith(literal, index)) {
                throw new IllegalArgumentException("Invalid literal");
            }
            index += literal.length();
            return new JsonLiteralValue(literal);
        }

        private JsonLiteralValue parseNumber() {
            int start = index;
            if (input.charAt(index) == '-') {
                index++;
            }
            consumeDigits();
            if (peek('.')) {
                index++;
                consumeDigits();
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                consumeDigits();
            }
            return new JsonLiteralValue(input.substring(start, index));
        }

        private void consumeDigits() {
            int start = index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Invalid number");
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= input.length() || input.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < input.length() && input.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }
    }
}
