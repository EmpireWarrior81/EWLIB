package empire.ewlib.config.json5;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON5 parser. It accepts everything owo-lib (Jankson) writes, and the
 * usual hand-edited extras: comments, unquoted keys, single quotes and trailing commas.
 * <p>
 * Objects become {@link LinkedHashMap}s, arrays become {@link ArrayList}s, numbers become
 * {@link Long} or {@link Double}, and {@code null} stays {@code null}.
 */
public final class Json5Reader {

    private final String text;
    private int pos = 0;

    private Json5Reader(String text) {
        this.text = text;
    }

    /**
     * Parse a JSON5 document whose root is an object
     *
     * @throws Json5Exception if the text is not valid JSON5
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readObject(String text) {
        var reader = new Json5Reader(text);
        reader.skipWhitespace();
        if (reader.peek() != '{') throw reader.error("Expected '{' at the start of the document");

        var result = reader.readValue();
        reader.skipWhitespace();
        if (reader.pos < reader.text.length()) throw reader.error("Unexpected content after the root object");

        return (Map<String, Object>) result;
    }

    private @Nullable Object readValue() {
        this.skipWhitespace();
        char c = this.peek();

        return switch (c) {
            case '{' -> this.readMap();
            case '[' -> this.readList();
            case '"', '\'' -> this.readString();
            default -> {
                if (c == '-' || c == '+' || c == '.' || Character.isDigit(c)) yield this.readNumber();

                var word = this.readIdentifier();
                yield switch (word) {
                    case "true" -> true;
                    case "false" -> false;
                    case "null" -> null;
                    case "Infinity" -> Double.POSITIVE_INFINITY;
                    case "NaN" -> Double.NaN;
                    default -> throw this.error("Unexpected value '" + word + "'");
                };
            }
        };
    }

    private Map<String, Object> readMap() {
        var map = new LinkedHashMap<String, Object>();
        this.expect('{');

        while (true) {
            this.skipWhitespace();
            if (this.peek() == '}') {
                this.pos++;
                return map;
            }

            char c = this.peek();
            String key = c == '"' || c == '\'' ? this.readString() : this.readIdentifier();
            if (key.isEmpty()) throw this.error("Expected an object key");

            this.skipWhitespace();
            this.expect(':');
            map.put(key, this.readValue());

            this.skipWhitespace();
            if (this.peek() == ',') {
                this.pos++;
            } else if (this.peek() != '}') {
                throw this.error("Expected ',' or '}'");
            }
        }
    }

    private List<Object> readList() {
        var list = new ArrayList<>();
        this.expect('[');

        while (true) {
            this.skipWhitespace();
            if (this.peek() == ']') {
                this.pos++;
                return list;
            }

            list.add(this.readValue());

            this.skipWhitespace();
            if (this.peek() == ',') {
                this.pos++;
            } else if (this.peek() != ']') {
                throw this.error("Expected ',' or ']'");
            }
        }
    }

    private String readString() {
        char quote = this.next();
        var builder = new StringBuilder();

        while (true) {
            char c = this.next();
            if (c == quote) return builder.toString();

            if (c != '\\') {
                builder.append(c);
                continue;
            }

            char escaped = this.next();
            switch (escaped) {
                case 'n' -> builder.append('\n');
                case 't' -> builder.append('\t');
                case 'r' -> builder.append('\r');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case '0' -> builder.append('\0');
                case 'u' -> {
                    if (this.pos + 4 > this.text.length()) throw this.error("Incomplete unicode escape");
                    builder.append((char) Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16));
                    this.pos += 4;
                }
                // line continuation
                case '\n' -> {}
                case '\r' -> {
                    if (this.pos < this.text.length() && this.text.charAt(this.pos) == '\n') this.pos++;
                }
                default -> builder.append(escaped);
            }
        }
    }

    private Object readNumber() {
        int start = this.pos;
        while (this.pos < this.text.length()) {
            char c = this.text.charAt(this.pos);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '+') {
                this.pos++;
            } else {
                break;
            }
        }

        var raw = this.text.substring(start, this.pos);
        var unsigned = raw.startsWith("+") || raw.startsWith("-") ? raw.substring(1) : raw;
        boolean negative = raw.startsWith("-");

        try {
            if (unsigned.equals("Infinity")) return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            if (unsigned.equals("NaN")) return Double.NaN;

            if (unsigned.startsWith("0x") || unsigned.startsWith("0X")) {
                long value = Long.parseLong(unsigned.substring(2), 16);
                return negative ? -value : value;
            }

            if (unsigned.contains(".") || unsigned.contains("e") || unsigned.contains("E")) {
                return Double.parseDouble(raw);
            }

            return Long.parseLong(raw.startsWith("+") ? unsigned : raw);
        } catch (NumberFormatException e) {
            throw this.error("Invalid number '" + raw + "'");
        }
    }

    private String readIdentifier() {
        int start = this.pos;
        while (this.pos < this.text.length()) {
            char c = this.text.charAt(this.pos);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                this.pos++;
            } else {
                break;
            }
        }

        return this.text.substring(start, this.pos);
    }

    private void skipWhitespace() {
        while (this.pos < this.text.length()) {
            char c = this.text.charAt(this.pos);

            if (Character.isWhitespace(c) || c == '﻿') {
                this.pos++;
            } else if (this.text.startsWith("//", this.pos)) {
                while (this.pos < this.text.length() && this.text.charAt(this.pos) != '\n') this.pos++;
            } else if (this.text.startsWith("/*", this.pos)) {
                int end = this.text.indexOf("*/", this.pos + 2);
                if (end < 0) throw this.error("Unclosed block comment");
                this.pos = end + 2;
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (this.pos >= this.text.length()) throw this.error("Unexpected end of file");
        return this.text.charAt(this.pos);
    }

    private char next() {
        char c = this.peek();
        this.pos++;
        return c;
    }

    private void expect(char expected) {
        if (this.next() != expected) {
            this.pos--;
            throw this.error("Expected '" + expected + "'");
        }
    }

    private Json5Exception error(String message) {
        int line = 1, column = 1;
        for (int i = 0; i < Math.min(this.pos, this.text.length()); i++) {
            if (this.text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }

        return new Json5Exception(message + " (line " + line + ", column " + column + ")");
    }

    public static class Json5Exception extends RuntimeException {
        public Json5Exception(String message) {
            super(message);
        }
    }
}
