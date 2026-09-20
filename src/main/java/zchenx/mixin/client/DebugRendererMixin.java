package zchenx.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zchenx.client.ModConfig;

/**
 * Draws the hitbox of the aimed entity in the configured style while vanilla's own entity hitbox
 * display (F3+B) is <b>off</b>. This is opt-in through {@code hitbox_always_show} (default off).
 */
@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
    @Unique
    private static final Logger EECHT_LOGGER = LoggerFactory.getLogger("easy_entity_crosshair_hitbox_tint");
    @Unique
    private static boolean eecht$drawingFailed;
    @Unique
    private static boolean eecht$drawingLogged;

    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void eecht$emitAimedEntityHitbox(Frustum frustum, double camX, double camY, double camZ, float partialTicks, CallbackInfo ci) {
        if (eecht$drawingFailed) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (!ModConfig.get().isHitboxAlwaysShow()) {
            return;
        }
        if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            return;
        }

        Entity target = ModConfig.aimedEntity(minecraft);
        if (target == null || target.isInvisible() || target == minecraft.getCameraEntity()) {
            return;
        }

        GizmoStyle style = ModConfig.get().resolveHitboxStyle(target);
        if (style == null) {
            return;
        }

        try {
            Vec3 offset = target.getPosition(partialTicks).subtract(target.position());
            Gizmos.cuboid(target.getBoundingBox().move(offset), style);
            if (!eecht$drawingLogged) {
                eecht$drawingLogged = true;
                EECHT_LOGGER.info("[EasyCrosshairHitboxTint] drawing aimed entity hitbox for {} (vanilla hitbox display is off)", target.getType());
            }
        } catch (Throwable t) {
            eecht$drawingFailed = true;
            EECHT_LOGGER.warn("[EasyCrosshairHitboxTint] disabled aimed entity hitbox drawing after an error", t);
        }
    }
}
