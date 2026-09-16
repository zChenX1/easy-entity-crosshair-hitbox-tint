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
import zchenx.client.ModConfig;

/**
 * Replaces the single {@code blitSprite} call that draws the crosshair so the sprite can be tinted
 * (and made translucent) instead of being drawn with the vanilla colour-inverting pipeline.
 *
 * <p>Only the crosshair sprite itself is touched: the attack indicator sprites are passed through
 * unchanged, and the sprite identifier is never replaced, so resource packs that ship their own
 * {@code assets/minecraft/textures/gui/sprites/hud/crosshair.png} keep working.
 */
@Mixin(Hud.class)
public class HudMixin {
    @Redirect(
            method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void easycrosshairmodify$drawCrosshair(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                                                    int x, int y, int width, int height) {
        int color = ModConfig.get().resolveCrosshairColor(Minecraft.getInstance(), sprite);
        if (color == ModConfig.VANILLA) {
            graphics.blitSprite(pipeline, sprite, x, y, width, height);
        } else {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, color);
        }
    }
}
