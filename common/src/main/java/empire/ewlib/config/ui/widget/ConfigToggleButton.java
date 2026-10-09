package empire.ewlib.config.ui.widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * An on/off button showing {@code [✔] Enabled} or {@code [❌] Disabled}
 */
public class ConfigToggleButton extends Button {

    protected static final Component ENABLED_MESSAGE = Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("✔").withColor(0x28FFBF))
            .append(Component.literal("]").withStyle(ChatFormatting.GRAY))
            .append(" ")
            .append(Component.translatable("text.ewlib.config.boolean_toggle.enabled"));

    protected static final Component DISABLED_MESSAGE = Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("❌").withColor(0xEB1D36))
            .append(Component.literal("]").withStyle(ChatFormatting.GRAY))
            .append(" ")
            .append(Component.translatable("text.ewlib.config.boolean_toggle.disabled"));

    protected boolean enabled;
    protected Runnable onToggle = () -> {};

    public ConfigToggleButton(int width, int height, boolean enabled) {
        super(0, 0, width, height, Component.empty(), button -> {}, DEFAULT_NARRATION);
        this.enabled(enabled);
    }

    @Override
    public void onPress() {
        this.enabled(!this.enabled);
        this.onToggle.run();
    }

    public void onToggle(Runnable onToggle) {
        this.onToggle = onToggle;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
        this.setMessage(enabled ? ENABLED_MESSAGE : DISABLED_MESSAGE);
    }

    public boolean enabled() {
        return this.enabled;
    }
}
