package empire.ewlib.config.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A text box which colors its text green while the input is valid and red while it is not
 */
public class ConfigTextBox extends EditBox {

    public static final int VALID_COLOR = 0xFF28FFBF;
    public static final int INVALID_COLOR = 0xFFEB1D36;

    private final List<Consumer<String>> listeners = new ArrayList<>();
    private Predicate<String> validator = s -> true;

    public ConfigTextBox(int width, int height) {
        super(Minecraft.getInstance().font, 0, 0, width, height, Component.empty());
        this.setMaxLength(Integer.MAX_VALUE);
        this.setResponder(value -> {
            this.updateColor();
            for (var listener : this.listeners) listener.accept(value);
        });
    }

    /**
     * Mark input as valid or invalid with the given predicate
     */
    public ConfigTextBox validator(Predicate<String> validator) {
        this.validator = validator;
        this.updateColor();
        return this;
    }

    public void onChanged(Consumer<String> listener) {
        this.listeners.add(listener);
    }

    public boolean isValid() {
        return this.validator.test(this.getValue());
    }

    private void updateColor() {
        this.setTextColor(this.isValid() ? VALID_COLOR : INVALID_COLOR);
    }
}
