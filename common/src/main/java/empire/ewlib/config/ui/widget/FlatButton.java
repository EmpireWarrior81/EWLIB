package empire.ewlib.config.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button without the vanilla texture: transparent, with a dark highlight while hovered
 */
public class FlatButton extends Button {

    public FlatButton(int width, int height, Component message, OnPress onPress) {
        super(0, 0, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.active && this.isHoveredOrFocused()) {
            graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x77000000);
        }

        this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }
}
