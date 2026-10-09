package empire.ewlib.config.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * A section title with a fading line on either side
 */
public class SectionHeaderEntry implements ConfigEntry {

    protected static final int TOP_MARGIN = 10;
    protected static final int HEIGHT = 20;

    protected final Component title;
    protected final Component styledTitle;
    protected int x, y, width;

    public SectionHeaderEntry(Component title) {
        this.title = title;
        this.styledTitle = title.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    }

    public Component title() {
        return this.title;
    }

    @Override
    public int height() {
        return TOP_MARGIN + HEIGHT;
    }

    @Override
    public void layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    @Override
    public int x() {
        return this.x;
    }

    @Override
    public int y() {
        return this.y;
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        var font = Minecraft.getInstance().font;

        int lineWidth = (int) (this.width * .35f);
        int labelWidth = font.width(this.styledTitle);
        int totalWidth = lineWidth + 5 + labelWidth + 5 + lineWidth;

        int left = this.x + (this.width - totalWidth) / 2;
        int centerY = this.y + TOP_MARGIN + HEIGHT / 2;

        ConfigRendering.horizontalGradient(graphics, left, centerY - 1, left + lineWidth, centerY + 1, 0x00000000, 0xFFFFFFFF);
        graphics.text(font, this.styledTitle, left + lineWidth + 5, centerY - 4, 0xFFFFFFFF, true);
        int rightLine = left + lineWidth + 5 + labelWidth + 5;
        ConfigRendering.horizontalGradient(graphics, rightLine, centerY - 1, rightLine + lineWidth, centerY + 1, 0xFFFFFFFF, 0x00000000);
    }

    @Override
    public String searchText() {
        return this.title.getString().toLowerCase(Locale.ROOT);
    }
}
