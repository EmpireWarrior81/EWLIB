package empire.ewlib.config.ui.widget;

import empire.ewlib.config.ConfigTypes;
import empire.ewlib.config.Option;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

/**
 * A button cycling through the values of an enum option:
 * left click goes forward, right click goes back
 */
public class ConfigEnumButton extends Button.Plain {

    protected final Option<?> option;
    protected final Enum<?>[] values;
    protected int selectedIndex;
    protected boolean wasRightClicked = false;
    protected Runnable onChange = () -> {};

    public ConfigEnumButton(int width, int height, Option<?> option, Enum<?> selected) {
        super(0, 0, width, height, Component.empty(), button -> {}, DEFAULT_NARRATION);
        this.option = option;
        this.values = (Enum<?>[]) option.clazz().getEnumConstants();
        this.select(selected);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.wasRightClicked = event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        super.onClick(event, doubleClick);
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return buttonInfo.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT || super.isValidClickButton(buttonInfo);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        int step = this.wasRightClicked || input.hasShiftDown() ? -1 : 1;
        this.selectedIndex = Math.floorMod(this.selectedIndex + step, this.values.length);
        this.wasRightClicked = false;

        this.updateMessage();
        this.onChange.run();
    }

    public void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public void select(Enum<?> value) {
        for (int i = 0; i < this.values.length; i++) {
            if (this.values[i] == value) this.selectedIndex = i;
        }
        this.updateMessage();
    }

    public Enum<?> selected() {
        return this.values[this.selectedIndex];
    }

    protected void updateMessage() {
        var enumName = StringUtils.uncapitalize(this.option.clazz().getSimpleName());
        var valueName = ConfigTypes.enumValueName(this.selected());

        var optionValueKey = this.option.translationKey() + ".value." + valueName;
        this.setMessage(I18n.exists(optionValueKey)
                ? Component.translatable(optionValueKey)
                : Component.translatable("text.config." + this.option.configName() + ".enum." + enumName + "." + valueName));
    }
}
