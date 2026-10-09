package empire.ewlib.config.ui;

import empire.ewlib.config.ConfigTypes;
import empire.ewlib.config.Option;
import empire.ewlib.config.annotation.RangeConstraint;
import empire.ewlib.config.ui.widget.ConfigEnumButton;
import empire.ewlib.config.ui.widget.ConfigSlider;
import empire.ewlib.config.ui.widget.ConfigTextBox;
import empire.ewlib.config.ui.widget.ConfigToggleButton;
import empire.ewlib.config.ui.widget.FlatButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * The built-in option rows
 */
public final class OptionComponents {

    private OptionComponents() {}

    /**
     * A row with one value control and a reset button
     */
    public abstract static class WithReset extends OptionComponent {

        protected final Button resetButton;

        protected WithReset(Option<?> option) {
            super(option);
            this.resetButton = resetButton(this::resetToDefault);
        }

        protected abstract AbstractWidget valueWidget();

        protected abstract void resetToDefault();

        /**
         * Call after the value control changes, to update the reset button
         */
        protected void onValueChanged() {
            this.resetButton.active = !Objects.equals(this.parsedValue(), this.option.defaultValue());
        }

        @Override
        protected void layoutControls(int right, int centerY) {
            this.resetButton.setPosition(right - 5 - this.resetButton.getWidth(), centerY - this.resetButton.getHeight() / 2);

            var value = this.valueWidget();
            value.setPosition(this.resetButton.getX() - 5 - value.getWidth(), centerY - value.getHeight() / 2);
        }
    }

    public static class BooleanToggle extends WithReset {

        protected final ConfigToggleButton toggle;

        public BooleanToggle(Option<Boolean> option) {
            super(option);
            this.toggle = this.addWidget(new ConfigToggleButton(VALUE_WIDTH, CONTROL_HEIGHT, option.value()));
            this.toggle.onToggle(this::onValueChanged);
            this.addWidget(this.resetButton);
            this.onValueChanged();
        }

        @Override
        protected AbstractWidget valueWidget() {
            return this.toggle;
        }

        @Override
        protected void resetToDefault() {
            this.toggle.enabled((Boolean) this.option.defaultValue());
            this.onValueChanged();
        }

        @Override
        public Object parsedValue() {
            return this.toggle.enabled();
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        protected String valueSearchText() {
            return this.toggle.getMessage().getString();
        }
    }

    public static class EnumCycle extends WithReset {

        protected final ConfigEnumButton button;

        public EnumCycle(Option<? extends Enum<?>> option) {
            super(option);
            this.button = this.addWidget(new ConfigEnumButton(VALUE_WIDTH, CONTROL_HEIGHT, option, option.value()));
            this.button.onChange(this::onValueChanged);
            this.addWidget(this.resetButton);
            this.onValueChanged();
        }

        @Override
        protected AbstractWidget valueWidget() {
            return this.button;
        }

        @Override
        protected void resetToDefault() {
            this.button.select((Enum<?>) this.option.defaultValue());
            this.onValueChanged();
        }

        @Override
        public Object parsedValue() {
            return this.button.selected();
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        protected String valueSearchText() {
            return this.button.getMessage().getString();
        }
    }

    /**
     * A text box for strings, identifiers and unconstrained numbers
     */
    public static class TextBox<T> extends WithReset {

        protected final ConfigTextBox textBox;
        protected final Function<T, String> toString;
        protected final Function<String, @Nullable T> parser;

        /**
         * @param parser Turns the entered text into a value, or returns {@code null} if the text is invalid
         */
        @SuppressWarnings("unchecked")
        public TextBox(Option<T> option, Function<T, String> toString, Function<String, @Nullable T> parser) {
            super(option);
            this.toString = toString;
            this.parser = parser;

            this.textBox = this.addWidget(new ConfigTextBox(VALUE_WIDTH, CONTROL_HEIGHT));
            this.textBox.setValue(toString.apply(option.value()));
            this.textBox.moveCursorToStart();
            this.textBox.validator(text -> {
                var parsed = this.parser.apply(text);
                var constraint = this.option.constraint();
                return parsed != null && (constraint == null || constraint.test(parsed));
            });
            this.textBox.onChanged(text -> this.onValueChanged());

            this.addWidget(this.resetButton);
            this.onValueChanged();
        }

        @Override
        protected AbstractWidget valueWidget() {
            return this.textBox;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void resetToDefault() {
            this.textBox.setValue(this.toString.apply((T) this.option.defaultValue()));
            this.textBox.moveCursorToStart();
        }

        @Override
        protected void onValueChanged() {
            this.resetButton.active = !Objects.equals(this.textBox.getValue(), this.defaultText());
        }

        @SuppressWarnings("unchecked")
        private String defaultText() {
            return this.toString.apply((T) this.option.defaultValue());
        }

        @Override
        public Object parsedValue() {
            return this.parser.apply(this.textBox.getValue());
        }

        @Override
        public boolean isValid() {
            return this.textBox.isValid();
        }

        @Override
        protected String valueSearchText() {
            return this.textBox.getValue();
        }
    }

    /**
     * A slider for numbers with a {@link RangeConstraint}, which
     * can be switched to a text box with the {@code ✎} button
     */
    public static class Range extends OptionComponent {

        protected static final Component EDIT_AS_TEXT = Component.translatable("text.ewlib.config.button.range.edit_as_text");
        protected static final Component EDIT_WITH_SLIDER = Component.translatable("text.ewlib.config.button.range.edit_with_slider");

        protected final ConfigSlider slider;
        protected final ConfigTextBox textBox;
        protected final FlatButton modeToggle;
        protected final Button resetButton;
        protected final int decimalPlaces;

        protected boolean textMode = false;

        public Range(Option<? extends Number> option) {
            super(option);

            var constraint = option.backingField().field().getAnnotation(RangeConstraint.class);
            this.decimalPlaces = ConfigTypes.isFloatingPointType(option.clazz()) ? constraint.decimalPlaces() : 0;

            this.modeToggle = this.addWidget(new FlatButton(this.font.width("✎") + 8, 16, Component.literal("✎"), button -> this.toggleMode()));
            this.modeToggle.setTooltip(Tooltip.create(EDIT_AS_TEXT));

            this.slider = this.addWidget(new ConfigSlider(VALUE_WIDTH, CONTROL_HEIGHT, constraint.min(), constraint.max(), this.decimalPlaces, option.value().doubleValue()));
            this.slider.onChange(this::onValueChanged);

            this.textBox = this.addWidget(new ConfigTextBox(VALUE_WIDTH, CONTROL_HEIGHT));
            this.textBox.setValue(option.value().toString());
            this.textBox.validator(text -> {
                var parsed = ConfigTypes.parseNumber(text, this.option.clazz());
                var optionConstraint = this.option.constraint();
                return parsed != null && (optionConstraint == null || optionConstraint.test(parsed));
            });
            this.textBox.onChanged(text -> this.onValueChanged());
            this.textBox.visible = false;

            this.resetButton = this.addWidget(resetButton(this::resetToDefault));
            this.onValueChanged();
        }

        protected void toggleMode() {
            if (this.textMode) {
                if (this.textBox.isValid()) {
                    this.slider.setDiscreteValue(((Number) this.parsedValue()).doubleValue());
                }
            } else {
                this.textBox.setValue(this.formatValue(this.slider.discreteValue()));
                this.textBox.moveCursorToStart();
            }

            this.textMode = !this.textMode;
            this.modeToggle.setTooltip(Tooltip.create(this.textMode ? EDIT_WITH_SLIDER : EDIT_AS_TEXT));
        }

        @Override
        public boolean isWidgetShown(AbstractWidget widget) {
            if (widget == this.slider) return !this.textMode;
            if (widget == this.textBox) return this.textMode;
            return true;
        }

        protected void resetToDefault() {
            double defaultValue = ((Number) this.option.defaultValue()).doubleValue();
            this.slider.setDiscreteValue(defaultValue);
            this.textBox.setValue(this.formatValue(defaultValue));
            this.textBox.moveCursorToStart();
            this.onValueChanged();
        }

        protected void onValueChanged() {
            this.resetButton.active = this.isValid() && !Objects.equals(this.parsedValue(), this.option.defaultValue());
        }

        protected String formatValue(double value) {
            var converted = ConfigTypes.convertNumber(value, this.option.clazz());
            return converted.toString();
        }

        @Override
        protected void layoutControls(int right, int centerY) {
            this.resetButton.setPosition(right - 5 - this.resetButton.getWidth(), centerY - CONTROL_HEIGHT / 2);

            int valueX = this.resetButton.getX() - 5 - 1 - VALUE_WIDTH;
            this.slider.setPosition(valueX, centerY - CONTROL_HEIGHT / 2);
            this.textBox.setPosition(valueX, centerY - CONTROL_HEIGHT / 2);

            this.modeToggle.setPosition(valueX - 1 - 2 - this.modeToggle.getWidth(), centerY - this.modeToggle.getHeight() / 2);
        }

        @Override
        public Object parsedValue() {
            if (this.textMode) {
                var parsed = ConfigTypes.parseNumber(this.textBox.getValue(), this.option.clazz());
                return parsed == null ? this.option.value() : parsed;
            }

            return ConfigTypes.convertNumber(this.slider.discreteValue(), this.option.clazz());
        }

        @Override
        public boolean isValid() {
            return !this.textMode || this.textBox.isValid();
        }

        @Override
        protected String valueSearchText() {
            return String.valueOf(this.parsedValue());
        }
    }
}
