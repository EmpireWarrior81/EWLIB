package empire.ewlib.config.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A button without the vanilla texture: transparent, with a dark highlight while hovered
 */
public class FlatButton extends Button {

    public FlatButton(int width, int height, Component message, OnPress onPress) {
        super(0, 0, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.active && this.isHoveredOrFocused()) {
            graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x77000000);
        }

        this.renderString(graphics, Minecraft.getInstance().font, 0xFFFFFF | Mth.ceil(this.alpha * 255.0F) << 24);
    }
}
