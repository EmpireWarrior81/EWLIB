package empire.ewlib.config.ui;

import empire.ewlib.config.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The row for one option in a {@link ConfigScreen}: the option's name on the left and
 * its controls on the right. Edits stay in the row until the screen is closed, at which
 * point {@link #parsedValue()} is written back to the option.
 */
public abstract class OptionComponent implements ConfigEntry {

    public static final int HEIGHT = 32;
    public static final int PADDING = 5;
    public static final int VALUE_WIDTH = 120;
    public static final int CONTROL_HEIGHT = 20;

    protected final Option<?> option;
    protected final Component name;
    protected final Font font = Minecraft.getInstance().font;
    protected final List<AbstractWidget> widgets = new ArrayList<>();

    protected int x, y, width;

    protected OptionComponent(Option<?> option) {
        this.option = option;
        this.name = Component.translatable(option.translationKey());
    }

    /**
     * @return The value currently entered in this row's controls
     */
    public abstract Object parsedValue();

    /**
     * @return Whether the entered value can be saved
     */
    public abstract boolean isValid();

    /**
     * Position the controls inside the row, right-aligned
     */
    protected abstract void layoutControls(int right, int centerY);

    /**
     * @return The current value as shown to the search, e.g. "enabled" or "5"
     */
    protected abstract String valueSearchText();

    public Option<?> option() {
        return this.option;
    }

    public boolean hasChanged() {
        return !Objects.equals(this.option.value(), this.parsedValue());
    }

    protected <W extends AbstractWidget> W addWidget(W widget) {
        this.widgets.add(widget);
        return widget;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return this.widgets;
    }

    /**
     * @return Whether {@code widget} is currently part of the row; rows that swap
     * controls (like a slider that can turn into a text box) hide the inactive one
     */
    public boolean isWidgetShown(AbstractWidget widget) {
        return true;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public void layout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.layoutControls(x + width - PADDING, y + HEIGHT / 2);
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
        int nameX = this.x + PADDING;
        int controlsLeft = this.x + this.width;
        for (var widget : this.widgets) {
            if (this.isWidgetShown(widget)) controlsLeft = Math.min(controlsLeft, widget.getX());
        }

        // shorten the name instead of letting it run under the controls
        var name = this.font.width(this.name) <= controlsLeft - 4 - nameX
                ? this.name.getVisualOrderText()
                : Language.getInstance().getVisualOrder(FormattedText.composite(
                        this.font.substrByWidth(this.name, controlsLeft - 4 - nameX - this.font.width("...")),
                        FormattedText.of("...")));
        graphics.text(this.font, name, nameX, this.y + (HEIGHT - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);

        for (var widget : this.widgets) {
            if (widget.visible) widget.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    public String searchText() {
        return (this.name.getString() + " " + this.valueSearchText()).toLowerCase(Locale.ROOT);
    }

    /**
     * The {@code ⇄} button which resets an option to its default value
     */
    protected static Button resetButton(Runnable onPress) {
        var font = Minecraft.getInstance().font;
        return Button.builder(Component.literal("⇄"), button -> onPress.run())
                .size(font.width("⇄") + 8, CONTROL_HEIGHT)
                .build();
    }
}
