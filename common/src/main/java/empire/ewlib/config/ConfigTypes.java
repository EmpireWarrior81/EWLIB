package empire.ewlib.config;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Conversion between config field types and the plain values stored in the config file
 */
public final class ConfigTypes {

    private ConfigTypes() {}

    public static boolean isNumberType(Class<?> clazz) {
        return clazz == int.class || clazz == Integer.class
                || clazz == long.class || clazz == Long.class
                || clazz == float.class || clazz == Float.class
                || clazz == double.class || clazz == Double.class
                || clazz == short.class || clazz == Short.class
                || clazz == byte.class || clazz == Byte.class;
    }

    public static boolean isFloatingPointType(Class<?> clazz) {
        return clazz == float.class || clazz == Float.class || clazz == double.class || clazz == Double.class;
    }

    /**
     * Convert a number to the given numeric field type
     */
    public static Object convertNumber(Number number, Class<?> target) {
        if (target == int.class || target == Integer.class) return number.intValue();
        if (target == long.class || target == Long.class) return number.longValue();
        if (target == float.class || target == Float.class) return number.floatValue();
        if (target == double.class || target == Double.class) return number.doubleValue();
        if (target == short.class || target == Short.class) return number.shortValue();
        if (target == byte.class || target == Byte.class) return number.byteValue();
        throw new IllegalArgumentException(target + " is not a number type");
    }

    /**
     * Parse user input (for example from a text box) into the given numeric type
     *
     * @return The parsed number, or {@code null} if the input is not a valid number of that type
     */
    public static @Nullable Object parseNumber(String input, Class<?> target) {
        try {
            if (target == int.class || target == Integer.class) return Integer.parseInt(input);
            if (target == long.class || target == Long.class) return Long.parseLong(input);
            if (target == float.class || target == Float.class) return Float.parseFloat(input);
            if (target == double.class || target == Double.class) return Double.parseDouble(input);
            if (target == short.class || target == Short.class) return Short.parseShort(input);
            if (target == byte.class || target == Byte.class) return Byte.parseByte(input);
        } catch (NumberFormatException ignored) {}

        return null;
    }

    /**
     * @return Whether the config file can store values of {@code type}
     */
    public static boolean isSupported(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            var raw = (Class<?>) parameterized.getRawType();
            return (raw == List.class || raw == Set.class) && isSupported(parameterized.getActualTypeArguments()[0]);
        }

        if (!(type instanceof Class<?> clazz)) return false;
        return clazz == boolean.class || clazz == Boolean.class || clazz == String.class
                || clazz.isEnum() || clazz == ResourceLocation.class || isNumberType(clazz);
    }

    /**
     * Convert a value from the config file into a value of the field's type
     *
     * @throws IllegalArgumentException if the value cannot be converted
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Object fromFile(@Nullable Object value, Type type) {
        if (value == null) return null;

        if (type instanceof ParameterizedType parameterized) {
            var raw = (Class<?>) parameterized.getRawType();
            var elementType = parameterized.getActualTypeArguments()[0];
            if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected a list but got " + value);

            Collection<Object> result = raw == Set.class ? new LinkedHashSet<>() : new ArrayList<>();
            for (var element : list) {
                result.add(fromFile(element, elementType));
            }
            return result;
        }

        var clazz = (Class<?>) type;

        if (clazz == boolean.class || clazz == Boolean.class) {
            if (value instanceof Boolean) return value;
            throw new IllegalArgumentException("Expected true or false but got " + value);
        }

        if (isNumberType(clazz)) {
            if (value instanceof Number number) return convertNumber(number, clazz);
            throw new IllegalArgumentException("Expected a number but got " + value);
        }

        if (clazz == String.class) return value.toString();

        if (clazz == ResourceLocation.class) {
            var identifier = ResourceLocation.tryParse(value.toString());
            if (identifier == null) throw new IllegalArgumentException("Invalid identifier " + value);
            return identifier;
        }

        if (clazz.isEnum()) {
            var name = value.toString();
            for (var constant : clazz.getEnumConstants()) {
                if (((Enum) constant).name().equalsIgnoreCase(name)) return constant;
            }
            throw new IllegalArgumentException("Unknown value '" + name + "' for " + clazz.getSimpleName());
        }

        throw new IllegalArgumentException("Unsupported config type " + clazz.getName());
    }

    /**
     * Convert a field value into a value the config file writer understands
     */
    public static @Nullable Object toFile(@Nullable Object value) {
        if (value instanceof Collection<?> collection) {
            var list = new ArrayList<>();
            for (var element : collection) list.add(toFile(element));
            return list;
        }

        if (value instanceof ResourceLocation identifier) return identifier.toString();
        return value;
    }

    /**
     * @return The lower-case name used for an enum constant in translation keys
     */
    public static String enumValueName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
