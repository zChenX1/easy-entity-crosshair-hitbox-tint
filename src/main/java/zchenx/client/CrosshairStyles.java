package zchenx.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pixel-art markers drawn around the crosshair to show which attack the player is about to perform.
 *
 * <p>Everything is drawn with plain 1x1 rectangles on top of the vanilla crosshair, so it works with
 * any resource pack and needs no extra texture.
 */
public final class CrosshairStyles {
    private CrosshairStyles() {
    }

    /** Draws all enabled markers around the crosshair sprite at {@code x, y, width, height}. */
    public static void drawOverlays(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                    int color, boolean crit, boolean knockback, boolean sweep) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        if (crit) {
            drawCrit(graphics, centerX, centerY, color);
        }
        if (knockback) {
            drawKnockback(graphics, centerX, centerY, color);
        }
        if (sweep) {
            drawSweep(graphics, centerX, centerY, color);
        }
    }

    /** Dashed diagonal strokes in the four corners: "critical hit". */
    private static void drawCrit(GuiGraphicsExtractor graphics, int centerX, int centerY, int color) {
        for (int i = 0; i < 7; i++) {
            if ((i & 1) != 0) {
                // Dashed: skip every other pixel so the strokes look like "- - -".
                continue;
            }
            int offset = 7 + i;
            pixel(graphics, centerX - offset, centerY - offset, color);
            pixel(graphics, centerX + offset - 1, centerY - offset, color);
            pixel(graphics, centerX - offset, centerY + offset - 1, color);
            pixel(graphics, centerX + offset - 1, centerY + offset - 1, color);
        }
    }

    /** A "^" chevron above the crosshair: sprinting knockback attack. */
    private static void drawKnockback(GuiGraphicsExtractor graphics, int centerX, int centerY, int color) {
        for (int i = 0; i <= 3; i++) {
            pixel(graphics, centerX - i, centerY - 10 - i, color);
            pixel(graphics, centerX + i, centerY - 10 - i, color);
        }
    }

    /** An upward opening half arc below the crosshair: sweep attack. */
    private static void drawSweep(GuiGraphicsExtractor graphics, int centerX, int centerY, int color) {
        int radius = 8;
        int arcCenterY = centerY + 5;
        for (int degrees = 18; degrees <= 162; degrees += 6) {
            double radians = Math.toRadians(degrees);
            int px = centerX + (int) Math.round(radius * Math.cos(radians));
            int py = arcCenterY + (int) Math.round(radius * Math.sin(radians));
            pixel(graphics, px, py, color);
        }
    }

    /** Draws a single pixel, like the vanilla GUI helpers do. */
    private static void pixel(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 1, y + 1, color);
    }
}
