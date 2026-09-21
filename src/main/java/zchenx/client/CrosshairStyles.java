package zchenx.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * The attack style markers of the crosshair (critical hit, sprint knockback, sweep attack).
 *
 * <p>Two modes are supported (see {@code attack_style_mode}):
 *
 * <ul>
 *   <li><b>decorate</b> (default): the vanilla crosshair stays and the marker is drawn around it;</li>
 *   <li><b>override</b>: the vanilla crosshair is hidden and replaced by the marker's crosshair
 *       texture, so a pack can supply a completely different crosshair per attack.</li>
 * </ul>
 *
 * <p>Everything comes from GUI sprites of this mod, so <b>resource packs can redraw it all</b> by
 * shipping the same paths with a higher priority (32x32, crosshair centre in the middle):
 *
 * <pre>
 * decorate: assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_crit.png
 *           .../crosshair_knockback.png
 *           .../crosshair_sweep.png
 * override: .../crosshair_crit_override.png
 *           .../crosshair_knockback_override.png
 *           .../crosshair_sweep_override.png
 * </pre>
 *
 * <p>The shipped textures are white, so {@code attack_style_color} tints them; a pack drawn in colour
 * can simply set the colour to {@code #FFFFFF}. When the sprites cannot be resolved (for example with
 * Fabric Loader alone, because 26.3 provides mod resources through Fabric API) the same shapes are
 * drawn pixel by pixel, so a missing texture is never shown.
 */
public final class CrosshairStyles {
    /** Sprite size; the texture centre is the crosshair centre. */
    public static final int SPRITE_SIZE = 32;

    private static final String NAMESPACE = "easy_entity_crosshair_hitbox_tint";

    private static final Identifier CRIT = sprite("hud/crosshair_crit");
    private static final Identifier KNOCKBACK = sprite("hud/crosshair_knockback");
    private static final Identifier SWEEP = sprite("hud/crosshair_sweep");
    private static final Identifier CRIT_OVERRIDE = sprite("hud/crosshair_crit_override");
    private static final Identifier KNOCKBACK_OVERRIDE = sprite("hud/crosshair_knockback_override");
    private static final Identifier SWEEP_OVERRIDE = sprite("hud/crosshair_sweep_override");

    private static final Identifier GUI_ATLAS = Identifier.withDefaultNamespace("textures/atlas/gui.png");
    private static final long SPRITE_CACHE_NANOS = 1_000_000_000L;

    /** The vanilla 15x15 crosshair shape, used by the pixel fallback of the override mode. */
    private static final int[][] VANILLA_CROSSHAIR = {
            {7, 3}, {7, 4}, {7, 5}, {7, 6}, {3, 7}, {4, 7}, {5, 7}, {6, 7}, {7, 7}, {8, 7}, {9, 7}, {10, 7}, {11, 7},
            {7, 8}, {7, 9}, {7, 10}, {7, 11}
    };

    private static long spritesCheckedAt;
    private static boolean decorateSprites;
    private static boolean overrideSprites;

    private CrosshairStyles() {
    }

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    /**
     * Draws the markers for the current attack.
     *
     * @param override when {@code true} the caller did not draw the vanilla crosshair, so a replacement
     *                 crosshair is drawn here instead
     */
    public static void drawMarkers(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                   int color, boolean crit, boolean knockback, boolean sweep, boolean override) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        int spriteX = centerX - SPRITE_SIZE / 2;
        int spriteY = centerY - SPRITE_SIZE / 2;
        checkSprites();

        if (override) {
            if (overrideSprites) {
                if (crit) {
                    blit(graphics, CRIT_OVERRIDE, spriteX, spriteY, color);
                }
                if (knockback) {
                    blit(graphics, KNOCKBACK_OVERRIDE, spriteX, spriteY, color);
                }
                if (sweep) {
                    blit(graphics, SWEEP_OVERRIDE, spriteX, spriteY, color);
                }
                return;
            }
            // No override textures available: draw the crosshair itself, then the markers.
            drawVanillaCrosshair(graphics, centerX, centerY, color);
        } else if (decorateSprites) {
            if (crit) {
                blit(graphics, CRIT, spriteX, spriteY, color);
            }
            if (knockback) {
                blit(graphics, KNOCKBACK, spriteX, spriteY, color);
            }
            if (sweep) {
                blit(graphics, SWEEP, spriteX, spriteY, color);
            }
            return;
        }

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

    private static void blit(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int color) {
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        graphics.blitSprite(pipeline, sprite, x, y, SPRITE_SIZE, SPRITE_SIZE, color);
    }

    /** Whether the GUI atlas contains our sprites; cached for a second so F3+T picks changes up. */
    private static void checkSprites() {
        long now = System.nanoTime();
        if (now - spritesCheckedAt < SPRITE_CACHE_NANOS) {
            return;
        }
        spritesCheckedAt = now;
        decorateSprites = false;
        overrideSprites = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        try {
            if (minecraft.getTextureManager().getTexture(GUI_ATLAS) instanceof TextureAtlas atlas) {
                decorateSprites = available(atlas, CRIT);
                overrideSprites = available(atlas, CRIT_OVERRIDE) && available(atlas, KNOCKBACK_OVERRIDE)
                        && available(atlas, SWEEP_OVERRIDE);
            }
        } catch (Throwable ignored) {
            // Any problem here simply means: draw the built-in pixel art.
        }
    }

    private static boolean available(TextureAtlas atlas, Identifier sprite) {
        TextureAtlasSprite resolved = atlas.getSprite(sprite);
        return !resolved.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    // ---- built-in fallback shapes (identical to the shipped textures) ----

    private static void drawVanillaCrosshair(GuiGraphicsExtractor graphics, int centerX, int centerY, int color) {
        for (int[] pixel : VANILLA_CROSSHAIR) {
            pixel(graphics, centerX - 7 + pixel[0], centerY - 7 + pixel[1], color);
        }
    }

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

    private static void drawKnockback(GuiGraphicsExtractor graphics, int centerX, int centerY, int color) {
        for (int i = 0; i <= 3; i++) {
            pixel(graphics, centerX - i, centerY - 10 - i, color);
            pixel(graphics, centerX + i, centerY - 10 - i, color);
        }
    }

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

    private static void pixel(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 1, y + 1, color);
    }
}
