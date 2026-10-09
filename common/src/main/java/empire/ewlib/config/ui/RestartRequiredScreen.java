package empire.ewlib.config.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Shown after closing a config screen in which an option that needs a restart
 * was changed: lets the player exit the game now or restart later
 */
public class RestartRequiredScreen extends Screen {

    protected final @Nullable Screen parent;

    public RestartRequiredScreen(@Nullable Screen parent) {
        super(Component.translatable("text.ewlib.config.must_restart"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var exitText = Component.translatable("text.ewlib.config.button.exit_minecraft");
        var ignoreText = Component.translatable("text.ewlib.config.button.ignore_restart");

        int exitWidth = this.font.width(exitText) + 10;
        int ignoreWidth = this.font.width(ignoreText) + 10;

        int buttonY = this.height / 2 - 16 + 9 + 25 + 5;
        int left = (this.width - (exitWidth + 10 + ignoreWidth + 10)) / 2 + 5;

        this.addRenderableWidget(Button.builder(exitText, button -> this.minecraft.stop())
                .bounds(left, buttonY, exitWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(ignoreText, button -> this.onClose())
                .bounds(left + exitWidth + 10, buttonY, ignoreWidth, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 16, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
