package empire.ewlib.config;

import empire.ewlib.EWLib;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * A single option of a config, backed by a field of the config model
 *
 * @param <T> The type of value this option holds
 */
public final class Option<T> {

    private final String configName;
    private final Key key;
    private final String translationKey;

    private final T defaultValue;
    private final Observable<T> mirror;
    private final BoundField<T> backingField;
    private final Class<T> clazz;

    private final @Nullable Constraint constraint;

    @SuppressWarnings("unchecked")
    Option(String configName, Key key, T defaultValue, Observable<T> mirror, BoundField<T> backingField, @Nullable Constraint constraint) {
        this.configName = configName;
        this.key = key;
        this.translationKey = "text.config." + configName + ".option." + key.asString();

        this.defaultValue = defaultValue;
        this.mirror = mirror;
        this.backingField = backingField;
        this.clazz = (Class<T>) backingField.field().getType();

        this.constraint = constraint;
    }

    /**
     * Update the value of this option, or do nothing if the value
     * does not satisfy this option's constraint
     */
    public void set(T value) {
        if (!this.verifyConstraint(value)) return;

        this.backingField.setValue(value);
        this.mirror.set(value);
    }

    public T value() {
        return this.mirror.get();
    }

    public T defaultValue() {
        return this.defaultValue;
    }

    /**
     * Run {@code observer} every time the value of this option changes
     */
    public void observe(Consumer<T> observer) {
        this.mirror.observe(observer);
    }

    /**
     * @return {@code true} if {@code value} satisfies this option's
     * constraint (or the option has none), otherwise logs a warning
     */
    public boolean verifyConstraint(T value) {
        if (this.constraint == null || this.constraint.test(value)) return true;

        EWLib.LOGGER.warn("Option {} in config '{}' could not be set to '{}' because it does not satisfy constraint: {}",
                this.key.asString(), this.configName, value, this.constraint.formatted());
        return false;
    }

    public Class<T> clazz() {
        return this.clazz;
    }

    public Key key() {
        return this.key;
    }

    public String configName() {
        return this.configName;
    }

    /**
     * @return The translation key of this option's name in the config screen,
     * {@code text.config.<config name>.option.<key>}
     */
    public String translationKey() {
        return this.translationKey;
    }

    public BoundField<T> backingField() {
        return this.backingField;
    }

    public @Nullable Constraint constraint() {
        return this.constraint;
    }

    @Override
    public String toString() {
        return this.configName + "/" + this.key.asString();
    }

    /**
     * The path of an option inside its config
     */
    public record Key(String asString) {
        public String name() {
            return this.asString;
        }
    }

    /**
     * A field of the config model, bound to the instance that owns it
     */
    public record BoundField<T>(Object owner, Field field) {

        @SuppressWarnings("unchecked")
        public T getValue() {
            try {
                return (T) this.field.get(this.owner);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not read config field " + this.field.getName(), e);
            }
        }

        public void setValue(T value) {
            try {
                this.field.set(this.owner, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not write config field " + this.field.getName(), e);
            }
        }

        public boolean hasAnnotation(Class<? extends Annotation> annotation) {
            return this.field.isAnnotationPresent(annotation);
        }
    }

    /**
     * A predicate which an option's value must satisfy, along with a readable description of it
     */
    public record Constraint(String formatted, java.util.function.Predicate<Object> predicate) {
        public boolean test(Object value) {
            return this.predicate.test(value);
        }
    }
}
