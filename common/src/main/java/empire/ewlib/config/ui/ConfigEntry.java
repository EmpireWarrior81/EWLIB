package empire.ewlib.config.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.List;

/**
 * One entry in the scrolling option panel of a {@link ConfigScreen}:
 * an option row or a section header
 */
public interface ConfigEntry {

    int height();

    /**
     * Position this entry and its widgets. Called every frame, since
     * scrolling and the sections sidebar move entries around.
     */
    void layout(int x, int y, int width);

    int x();

    int y();

    int width();

    void extract(GuiGraphics graphics, int mouseX, int mouseY, float a);

    /**
     * @return The vanilla widgets of this entry, which receive input through the screen
     */
    default List<AbstractWidget> widgets() {
        return List.of();
    }

    /**
     * @return The lower-case text the config search matches against
     */
    String searchText();
}
