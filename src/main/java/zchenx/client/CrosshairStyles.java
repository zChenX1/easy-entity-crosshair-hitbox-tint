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
 * The attack style markers drawn around the crosshair (critical hit, sprint knockback, sweep attack).
 *
 * <p>Each marker is drawn from a normal GUI sprite, so <b>a resource pack can redraw it</b> by shipping
 * the same path with a higher priority:
 *
 * <ul>
 *   <li>{@code assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_crit.png}</li>
 *   <li>{@code assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_knockback.png}</li>
 *   <li>{@code assets/easy_entity_crosshair_hitbox_tint/textures/gui/sprites/hud/crosshair_sweep.png}</li>
 * </ul>
 *
 * <p>The built-in versions of those textures (shipped in this mod, 32x32, crosshair centre in the
 * middle, white so the configured colour tints them) are only visible when a mod-resource-loader is
 * present (Fabric API). When the sprite cannot be resolved - for example with Fabric Loader alone -
 * the same shapes are drawn pixel by pixel instead, so the feature never shows a missing texture.
 */
public final class CrosshairStyles {
    /** Sprite size; the marker texture centre is the crosshair centre. */
    public static final int SPRITE_SIZE = 32;

    private static final String NAMESPACE = "easy_entity_crosshair_hitbox_tint";
    public static final Identifier CRIT_SPRITE = Identifier.fromNamespaceAndPath(NAMESPACE, "hud/crosshair_crit");
    public static final Identifier KNOCKBACK_SPRITE = Identifier.fromNamespaceAndPath(NAMESPACE, "hud/crosshair_knockback");
    public static final Identifier SWEEP_SPRITE = Identifier.fromNamespaceAndPath(NAMESPACE, "hud/crosshair_sweep");

    private static final Identifier GUI_ATLAS = Identifier.withDefaultNamespace("textures/atlas/gui.png");
    private static final long SPRITE_CACHE_NANOS = 1_000_000_000L;

    private static long eecht$spriteCheckedAt;
    private static boolean eecht$spritesAvailable;

    private CrosshairStyles() {
    }

    /** Draws all enabled markers around the crosshair sprite at {@code x, y, width, height}. */
    public static void drawOverlays(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                    int color, boolean crit, boolean knockback, boolean sweep) {
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        int spriteX = centerX - SPRITE_SIZE / 2;
        int spriteY = centerY - SPRITE_SIZE / 2;
        boolean textured = spritesAvailable();
        if (crit) {
            if (textured) {
                blit(graphics, CRIT_SPRITE, spriteX, spriteY, color);
            } else {
                drawCrit(graphics, centerX, centerY, color);
            }
        }
        if (knockback) {
            if (textured) {
                blit(graphics, KNOCKBACK_SPRITE, spriteX, spriteY, color);
            } else {
                drawKnockback(graphics, centerX, centerY, color);
            }
        }
        if (sweep) {
            if (textured) {
                blit(graphics, SWEEP_SPRITE, spriteX, spriteY, color);
            } else {
                drawSweep(graphics, centerX, centerY, color);
            }
        }
    }

    private static void blit(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int color) {
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        graphics.blitSprite(pipeline, sprite, x, y, SPRITE_SIZE, SPRITE_SIZE, color);
    }

    /**
     * Whether the GUI atlas actually contains our marker textures. Cached for a second, so a resource
     * pack can add or remove them (F3+T) without restarting.
     */
    private static boolean spritesAvailable() {
        long now = System.nanoTime();
        if (now - eecht$spriteCheckedAt < SPRITE_CACHE_NANOS) {
            return eecht$spritesAvailable;
        }
        eecht$spriteCheckedAt = now;
        eecht$spritesAvailable = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        try {
            if (minecraft.getTextureManager().getTexture(GUI_ATLAS) instanceof TextureAtlas atlas) {
                TextureAtlasSprite sprite = atlas.getSprite(CRIT_SPRITE);
                eecht$spritesAvailable = !sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
            }
        } catch (Throwable ignored) {
            // Any problem here simply means: draw the built-in pixel art.
        }
        return eecht$spritesAvailable;
    }

    // ---- built-in fallback shapes (identical to the shipped textures) ----

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
