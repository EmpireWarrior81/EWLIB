package empire.ewlib.config.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing helpers the vanilla GUI does not provide
 */
public final class ConfigRendering {

    private ConfigRendering() {}

    /**
     * Fill a rectangle with a gradient running from {@code leftColor} to {@code rightColor}
     */
    public static void horizontalGradient(GuiGraphics graphics, int x0, int y0, int x1, int y1, int leftColor, int rightColor) {
        int width = x1 - x0;
        if (width <= 0) return;

        for (int column = 0; column < width; column++) {
            float progress = width == 1 ? 0 : column / (float) (width - 1);
            graphics.fill(x0 + column, y0, x0 + column + 1, y1, lerpArgb(progress, leftColor, rightColor));
        }
    }

    public static int lerpArgb(float progress, int from, int to) {
        int a = lerpChannel(progress, from >>> 24, to >>> 24);
        int r = lerpChannel(progress, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = lerpChannel(progress, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = lerpChannel(progress, from & 0xFF, to & 0xFF);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int lerpChannel(float progress, int from, int to) {
        return Math.round(from + (to - from) * progress);
    }

    /**
     * Cubic ease-in-out, as used by owo-ui animations
     */
    public static float easeCubic(float x) {
        return x < .5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }
}
