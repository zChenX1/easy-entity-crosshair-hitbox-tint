package zchenx.mixin.client;

import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zchenx.client.ModConfig;

/**
 * Re-colours the hitbox that vanilla draws for the entity the player is currently looking at
 * (the entity hitbox display, toggled in-game with F3+B).
 *
 * <p>This only ever replaces the {@link GizmoStyle} of the already existing main cuboid of the
 * targeted entity: no extra geometry is submitted, nothing happens while the feature is disabled,
 * and {@link ModConfig#resolveHitboxStyle(Entity)} returns {@code null} whenever the configured
 * value looks unsafe, in which case the vanilla style is used unchanged.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {
    @Unique
    private static final Logger EECHT_LOGGER = LoggerFactory.getLogger("easy_entity_crosshair_hitbox_tint");
    @Unique
    private static boolean eecht$tintLogged;
    @Unique
    private static Entity eecht$currentEntity;

    @Inject(method = "showHitboxes", at = @At("HEAD"))
    private void eecht$rememberEntity(Entity entity, float partialTicks, boolean isServerEntity, CallbackInfo ci) {
        eecht$currentEntity = entity;
    }

    @ModifyArg(
            method = "showHitboxes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/gizmos/Gizmos;cuboid(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/gizmos/GizmoStyle;)Lnet/minecraft/gizmos/GizmoProperties;",
                    ordinal = 0),
            index = 1)
    private GizmoStyle eecht$tintAimedHitbox(GizmoStyle style) {
        GizmoStyle tinted = ModConfig.get().resolveHitboxStyle(eecht$currentEntity);
        if (tinted == null) {
            return style;
        }
        if (!eecht$tintLogged) {
            eecht$tintLogged = true;
            EECHT_LOGGER.info("[EasyCrosshairHitboxTint] tinted aimed entity hitbox (vanilla F3+B display is on)");
        }
        return tinted;
    }
}
