package zchenx.mixin.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zchenx.client.CrosshairStyles;
import zchenx.client.ModConfig;

/**
 * Replaces the {@code blitSprite} calls inside {@code Hud#extractCrosshair} so that
 *
 * <ul>
 *   <li>the crosshair itself can be tinted / made translucent, and</li>
 *   <li>the attack indicator (the cooldown bar below the crosshair) can be tinted once the attack
 *       charge reaches the configured threshold.</li>
 * </ul>
 *
 * <p>Only those sprites are touched: everything else is passed through to the vanilla pipeline, the
 * sprite identifiers are never replaced (so resource packs keep working), and both handlers simply
 * forward to the original call when nothing has to change.
 */
@Mixin(Hud.class)
public class HudMixin {
    private static final String BLIT_SPRITE = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";
    private static final String BLIT_SPRITE_SLICED = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V";

    @Redirect(
            method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE", target = BLIT_SPRITE))
    private void easycrosshairmodify$drawSprite(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                                                 int x, int y, int width, int height) {
        int color = resolveColor(sprite);
        if (color == ModConfig.VANILLA) {
            graphics.blitSprite(pipeline, sprite, x, y, width, height);
        } else {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, color);
        }
        if (ModConfig.CROSSHAIR_PATH.equals(sprite.getPath())) {
            // Attack style markers are drawn on top of the crosshair itself.
            ModConfig.AttackStyle style = ModConfig.get().resolveAttackStyle(Minecraft.getInstance());
            if (style != null) {
                CrosshairStyles.drawOverlays(graphics, x, y, width, height, style.color(), style.crit(), style.knockback(), style.sweep());
            }
        }
    }

    @Redirect(
            method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE", target = BLIT_SPRITE_SLICED))
    private void easycrosshairmodify$drawSpriteSliced(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                                                       int spriteWidth, int spriteHeight, int textureX, int textureY,
                                                       int x, int y, int width, int height) {
        int color = resolveColor(sprite);
        if (color == ModConfig.VANILLA) {
            graphics.blitSprite(pipeline, sprite, spriteWidth, spriteHeight, textureX, textureY, x, y, width, height);
        } else {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, spriteWidth, spriteHeight, textureX, textureY, x, y, width, height, color);
        }
    }

    /** @return the tint to use, or {@link ModConfig#VANILLA} to draw the sprite exactly like vanilla. */
    private static int resolveColor(Identifier sprite) {
        ModConfig config = ModConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (ModConfig.CROSSHAIR_PATH.equals(sprite.getPath())) {
            return config.resolveCrosshairColor(minecraft, sprite);
        }
        if (config.isAttackIndicatorSprite(sprite) && !config.isAttackIndicatorBackground(sprite)) {
            // The bar background stays vanilla so the progress remains readable.
            return config.resolveAttackIndicatorColor(minecraft);
        }
        return ModConfig.VANILLA;
    }
}
