package empire.ewlib.config.ui.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A slider for a numeric option between {@code min} and {@code max}.
 * With {@code decimalPlaces == 0} it snaps to whole numbers.
 */
public class ConfigSlider extends AbstractSliderButton {

    protected final double min, max;
    protected final int decimalPlaces;
    protected Runnable onChange = () -> {};

    public ConfigSlider(int width, int height, double min, double max, int decimalPlaces, double initial) {
        super(0, 0, width, height, Component.empty(), 0);
        this.min = min;
        this.max = max;
        this.decimalPlaces = decimalPlaces;
        this.setDiscreteValue(initial);
    }

    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * @return The current value, rounded to this slider's number of decimal places
     */
    public double discreteValue() {
        double raw = this.min + this.value * (this.max - this.min);
        return BigDecimal.valueOf(raw).setScale(this.decimalPlaces, RoundingMode.HALF_UP).doubleValue();
    }

    public void setDiscreteValue(double discreteValue) {
        this.value = this.max == this.min ? 0 : Mth.clamp((discreteValue - this.min) / (this.max - this.min), 0, 1);
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        double value = this.discreteValue();
        this.setMessage(Component.literal(this.decimalPlaces == 0
                ? String.valueOf((long) value)
                : BigDecimal.valueOf(value).setScale(this.decimalPlaces, RoundingMode.HALF_UP).toPlainString()));
    }

    @Override
    protected void applyValue() {
        if (this.decimalPlaces == 0) {
            // snap the handle to the whole number it represents
            double snapped = this.discreteValue();
            this.value = this.max == this.min ? 0 : (snapped - this.min) / (this.max - this.min);
        }

        this.onChange.run();
    }
}
