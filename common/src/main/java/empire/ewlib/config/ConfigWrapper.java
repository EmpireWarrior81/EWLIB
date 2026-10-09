package empire.ewlib.config;

import empire.ewlib.EWLib;
import empire.ewlib.config.annotation.Comment;
import empire.ewlib.config.annotation.Config;
import empire.ewlib.config.annotation.RangeConstraint;
import empire.ewlib.config.json5.Json5Reader;
import empire.ewlib.config.json5.Json5Writer;
import empire.ewlib.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The common base class of all generated config wrappers, which holds
 * nearly all config functionality.
 * <p>
 * Do not extend this class yourself; annotate your config model with
 * {@link Config} and EWLIB's annotation processor generates the wrapper.
 *
 * @see Config
 */
public abstract class ConfigWrapper<C> {

    private static final Map<String, ConfigWrapper<?>> KNOWN_CONFIGS = new HashMap<>();

    protected final String name;
    protected final C instance;

    protected boolean loading = false;

    protected final Map<Option.Key, Option<?>> options = new LinkedHashMap<>();
    protected final Map<Option.Key, Option<?>> optionsView = Collections.unmodifiableMap(this.options);

    protected ConfigWrapper(Class<C> clazz) {
        var configAnnotation = clazz.getAnnotation(Config.class);
        if (configAnnotation == null) {
            throw new IllegalStateException("Config model class " + clazz.getName() + " must be annotated with @Config");
        }

        this.name = configAnnotation.name();
        this.instance = instantiate(clazz);

        if (KNOWN_CONFIGS.containsKey(this.name)) {
            throw new IllegalStateException("Config name '" + this.name + "' is already taken by "
                    + KNOWN_CONFIGS.get(this.name).getClass().getName());
        }
        KNOWN_CONFIGS.put(this.name, this);

        this.initializeOptions(configAnnotation.saveOnModification());
    }

    /**
     * Save this config to its file
     */
    public void save() {
        if (this.loading) return;

        var writer = new Json5Writer().beginObject();
        int index = 0;
        for (var option : this.options.values()) {
            var comment = option.backingField().field().getAnnotation(Comment.class);
            writer.entry(1, comment == null ? null : comment.value(), option.key().asString(),
                    ConfigTypes.toFile(option.value()), ++index == this.options.size());
        }
        writer.endObject(0);

        try {
            Files.createDirectories(this.fileLocation().getParent());
            Files.writeString(this.fileLocation(), writer.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            EWLib.LOGGER.warn("Could not save config {}", this.name, e);
        }
    }

    /**
     * Load this config from its file, or create the file if it does not exist yet.
     * Options missing from the file are reset to their default value, and invalid
     * values are reported in the log and ignored.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void load() {
        if (!Files.exists(this.fileLocation())) {
            this.save();
            return;
        }

        boolean missingOptions = false;
        try {
            this.loading = true;
            var configObject = Json5Reader.readObject(Files.readString(this.fileLocation(), StandardCharsets.UTF_8));

            for (var option : this.options.values()) {
                var key = option.key().asString();
                if (!configObject.containsKey(key)) {
                    ((Option) option).set(option.defaultValue());
                    missingOptions = true;
                    continue;
                }

                Object newValue;
                try {
                    newValue = ConfigTypes.fromFile(configObject.get(key), option.backingField().field().getGenericType());
                } catch (IllegalArgumentException e) {
                    EWLib.LOGGER.warn("Ignoring invalid value for option {} in config {}: {}", key, this.name, e.getMessage());
                    continue;
                }

                if (newValue == null && option.clazz().isPrimitive()) continue;
                if (!((Option) option).verifyConstraint(newValue)) continue;

                ((Option) option).set(newValue == null ? option.defaultValue() : newValue);
            }
        } catch (IOException | Json5Reader.Json5Exception e) {
            EWLib.LOGGER.warn("Could not load config {}", this.name, e);
            return;
        } finally {
            this.loading = false;
        }

        // write newly added options back to the file
        if (missingOptions) this.save();
    }

    /**
     * @return The name of this config, used for the file name and translation keys
     */
    public String name() {
        return this.name;
    }

    /**
     * @return The file this config is saved to, {@code config/<name>.json5}
     */
    public Path fileLocation() {
        return Platform.INSTANCE.configDir().resolve(this.name + ".json5");
    }

    /**
     * @return The option with the given key, or {@code null} if there is none
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable Option<T> optionForKey(Option.Key key) {
        return (Option<T>) this.options.get(key);
    }

    /**
     * @return The field of the config model with the given key, or {@code null} if there is none
     */
    public @Nullable Field fieldForKey(Option.Key key) {
        var option = this.options.get(key);
        return option == null ? null : option.backingField().field();
    }

    /**
     * @return A read-only view of all options in this config, in declaration order
     */
    public Map<Option.Key, Option<?>> allOptions() {
        return this.optionsView;
    }

    public void forEachOption(Consumer<Option<?>> action) {
        this.options.values().forEach(action);
    }

    /**
     * @return The config registered under {@code name}, or {@code null} if there is none
     */
    public static @Nullable ConfigWrapper<?> byName(String name) {
        return KNOWN_CONFIGS.get(name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void initializeOptions(boolean saveOnModification) {
        for (var field : this.instance.getClass().getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) continue;

            if (!Modifier.isPublic(modifiers) || Modifier.isFinal(modifiers)) {
                throw new IllegalStateException("Config option " + field.getName() + " in " + this.name + " must be public and not final");
            }

            if (!ConfigTypes.isSupported(field.getGenericType())) {
                throw new IllegalStateException("Config option " + field.getName() + " in " + this.name + " has unsupported type " + field.getGenericType().getTypeName());
            }

            var key = new Option.Key(field.getName());
            var boundField = new Option.BoundField<>(this.instance, field);
            var defaultValue = boundField.getValue();

            var observable = Observable.of(defaultValue);
            if (saveOnModification) observable.observe(value -> this.save());

            this.options.put(key, new Option(this.name, key, defaultValue, observable, boundField, constraintFor(field)));
        }
    }

    private static Option.@Nullable Constraint constraintFor(Field field) {
        var range = field.getAnnotation(RangeConstraint.class);
        if (range == null) return null;

        var type = field.getType();
        if (!ConfigTypes.isNumberType(type)) {
            throw new IllegalStateException("@RangeConstraint can only be applied to numeric fields, but " + field.getName() + " is " + type.getSimpleName());
        }

        if (type == long.class || type == Long.class) {
            return new Option.Constraint("Range from " + range.min() + " to " + range.max(),
                    o -> o != null && (Long) o >= range.min() && (Long) o <= range.max());
        }

        return new Option.Constraint("Range from " + range.min() + " to " + range.max(),
                o -> o != null && ((Number) o).doubleValue() >= range.min() && ((Number) o).doubleValue() <= range.max());
    }

    private static <C> C instantiate(Class<C> clazz) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Config model class " + clazz.getName() + " must provide a zero-args constructor", e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Could not instantiate config model class " + clazz.getName(), e);
        }
    }
}
