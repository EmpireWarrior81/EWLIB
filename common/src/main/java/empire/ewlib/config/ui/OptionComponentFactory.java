package empire.ewlib.config.ui;

import empire.ewlib.config.ConfigTypes;
import empire.ewlib.config.Option;
import empire.ewlib.config.annotation.RangeConstraint;
import net.minecraft.resources.Identifier;

/**
 * Creates the row for a config option in the {@link ConfigScreen}
 *
 * @param <T> The type of option this factory can create rows for
 */
@FunctionalInterface
public interface OptionComponentFactory<T> {

    OptionComponent make(Option<T> option);

    OptionComponentFactory<Boolean> BOOLEAN = OptionComponents.BooleanToggle::new;

    @SuppressWarnings({"unchecked", "rawtypes"})
    OptionComponentFactory<? extends Number> NUMBER = option -> {
        if (option.backingField().hasAnnotation(RangeConstraint.class)) {
            return new OptionComponents.Range(option);
        }

        return new OptionComponents.TextBox<>((Option) option, Object::toString, text -> ConfigTypes.parseNumber((String) text, option.clazz()));
    };

    OptionComponentFactory<String> STRING = option -> new OptionComponents.TextBox<>(option, s -> s, s -> s);

    OptionComponentFactory<Identifier> IDENTIFIER = option -> new OptionComponents.TextBox<>(option, Identifier::toString, Identifier::tryParse);

    @SuppressWarnings({"unchecked", "rawtypes"})
    OptionComponentFactory<? extends Enum<?>> ENUM = option -> new OptionComponents.EnumCycle((Option) option);
}
