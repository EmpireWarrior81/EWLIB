package empire.ewlib.config.json5;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Writes config files in the same layout owo-lib (Jankson) uses, so existing
 * config files keep looking the same after switching to EWLIB:
 * <pre>
 * {
 * 	// Comment above the option
 * 	"option": true,
 * 	"other": 5
 * }
 * </pre>
 */
public final class Json5Writer {

    private final StringBuilder builder = new StringBuilder();

    /**
     * Start an object at the given indentation depth
     */
    public Json5Writer beginObject() {
        this.builder.append("{\n");
        return this;
    }

    /**
     * Write one entry of an object, optionally preceded by a comment
     *
     * @param last Whether this is the last entry, which gets no trailing comma
     */
    public Json5Writer entry(int depth, @Nullable String comment, String key, @Nullable Object value, boolean last) {
        if (comment != null && !comment.isBlank()) {
            for (var line : comment.split("\n")) {
                this.indent(depth).append("// ").append(line).append('\n');
            }
        }

        this.indent(depth).append(quote(key)).append(": ");
        this.value(depth, value);
        if (!last) this.builder.append(',');
        this.builder.append('\n');
        return this;
    }

    public Json5Writer endObject(int depth) {
        this.indent(depth).append('}');
        return this;
    }

    @Override
    public String toString() {
        return this.builder.toString();
    }

    // written without pattern-matching switch, since Minecraft 1.20.1 runs on Java 17
    private void value(int depth, @Nullable Object value) {
        if (value == null) {
            this.builder.append("null");
        } else if (value instanceof Boolean bool) {
            this.builder.append(bool);
        } else if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
            this.builder.append(d.isNaN() ? "NaN" : d > 0 ? "Infinity" : "-Infinity");
        } else if (value instanceof Float f && (f.isNaN() || f.isInfinite())) {
            this.builder.append(f.isNaN() ? "NaN" : f > 0 ? "Infinity" : "-Infinity");
        } else if (value instanceof Number number) {
            this.builder.append(number);
        } else if (value instanceof Enum<?> enumValue) {
            this.builder.append(quote(enumValue.name()));
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                this.builder.append("{}");
                return;
            }

            this.beginObject();
            int index = 0;
            for (var entry : map.entrySet()) {
                this.entry(depth + 1, null, String.valueOf(entry.getKey()), entry.getValue(), ++index == map.size());
            }
            this.endObject(depth);
        } else if (value instanceof Collection<?> collection) {
            this.builder.append('[');
            int index = 0;
            for (var element : collection) {
                if (index++ > 0) this.builder.append(", ");
                this.value(depth, element);
            }
            this.builder.append(']');
        } else {
            this.builder.append(quote(value.toString()));
        }
    }

    private StringBuilder indent(int depth) {
        return this.builder.append("\t".repeat(depth));
    }

    public static String quote(String text) {
        var quoted = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                case '\b' -> quoted.append("\\b");
                case '\f' -> quoted.append("\\f");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }

        return quoted.append('"').toString();
    }
}
