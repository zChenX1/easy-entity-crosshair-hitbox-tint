package zchenx.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configuration of the mod, stored as TOML in {@code config/easy_entity_crosshair_hitbox_tint.toml}.
 *
 * <p>The file is re-read automatically when it is edited on disk, and the very same values can be
 * edited visually through the Mod Menu config screen ({@link ConfigScreen}).
 */
public final class ModConfig {
    /** Sentinel telling the mixins to keep the untouched vanilla rendering. */
    public static final int VANILLA = Integer.MIN_VALUE;

    public static final String CROSSHAIR_PATH = "hud/crosshair";
    private static final String ATTACK_INDICATOR_PREFIX = "hud/crosshair_attack_indicator_";
    private static final String ATTACK_INDICATOR_BACKGROUND = "hud/crosshair_attack_indicator_background";

    private static final Logger LOGGER = LoggerFactory.getLogger("easy_entity_crosshair_hitbox_tint");
    private static final String FILE_NAME = "easy_entity_crosshair_hitbox_tint.toml";
    private static final String LEGACY_JSON_NAME = "easy_entity_crosshair_hitbox_tint.json";
    private static final String DEFAULT_COLOR = "#FF0000";
    private static final int DEFAULT_RGB = 0xFF0000;
    private static final float DEFAULT_HITBOX_WIDTH = 2.5F;
    private static final float MIN_HITBOX_WIDTH = 0.5F;
    private static final float MAX_HITBOX_WIDTH = 8.0F;
    private static final float DEFAULT_ATTACK_THRESHOLD = 0.885F;
    private static final long CHECK_INTERVAL_MS = 1000L;
    private static final long RAYCAST_CACHE_NANOS = 10_000_000L;
    private static final Gson GSON = new Gson();

    private static ModConfig instance = new ModConfig();
    private static long nextCheck;
    private static long lastModified = -1L;
    private static Entity cachedRaycast;
    private static long cachedRaycastAt;

    // ---- crosshair ----
    boolean enabled = true;
    float opacity = 1.0F;
    String targetColor = DEFAULT_COLOR;
    int targetRgb = DEFAULT_RGB;
    List<String> targetEntities = new ArrayList<>();

    // ---- hitbox ----
    boolean hitboxEnabled = false;
    boolean hitboxAlwaysShow = false;
    String hitboxColor = DEFAULT_COLOR;
    int hitboxRgb = DEFAULT_RGB;
    float hitboxOpacity = 1.0F;
    float hitboxLineWidth = DEFAULT_HITBOX_WIDTH;

    // ---- attack indicator ----
    boolean attackIndicatorEnabled = false;
    String attackIndicatorColor = DEFAULT_COLOR;
    int attackIndicatorRgb = DEFAULT_RGB;
    float attackIndicatorThreshold = DEFAULT_ATTACK_THRESHOLD;

    // ---- attack crosshair styles ----
    boolean attackStyleEnabled = false;
    boolean attackStyleCrit = true;
    boolean attackStyleKnockback = true;
    boolean attackStyleSweep = true;
    String attackStyleColor = DEFAULT_COLOR;
    int attackStyleRgb = DEFAULT_RGB;
    boolean attackStyleOverride = false;

    /** The markers to draw around the crosshair, resolved once per frame. */
    public record AttackStyle(int color, boolean crit, boolean knockback, boolean sweep) {
    }

    private ModConfig() {
    }

    /** Returns the current configuration, reloading the file when it changed on disk. */
    public static ModConfig get() {
        long now = System.currentTimeMillis();
        if (now >= nextCheck) {
            nextCheck = now + CHECK_INTERVAL_MS;
            reload();
        }
        return instance;
    }

    public static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static Path legacyPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(LEGACY_JSON_NAME);
    }

    /** A copy of the current values: the config screen edits the copy, so edits can be discarded. */
    static ModConfig copy() {
        ModConfig copy = new ModConfig();
        copy.enabled = instance.enabled;
        copy.opacity = instance.opacity;
        copy.targetColor = instance.targetColor;
        copy.targetRgb = instance.targetRgb;
        copy.targetEntities = new ArrayList<>(instance.targetEntities);
        copy.hitboxEnabled = instance.hitboxEnabled;
        copy.hitboxAlwaysShow = instance.hitboxAlwaysShow;
        copy.hitboxColor = instance.hitboxColor;
        copy.hitboxRgb = instance.hitboxRgb;
        copy.hitboxOpacity = instance.hitboxOpacity;
        copy.hitboxLineWidth = instance.hitboxLineWidth;
        copy.attackIndicatorEnabled = instance.attackIndicatorEnabled;
        copy.attackIndicatorColor = instance.attackIndicatorColor;
        copy.attackIndicatorRgb = instance.attackIndicatorRgb;
        copy.attackIndicatorThreshold = instance.attackIndicatorThreshold;
        copy.attackStyleEnabled = instance.attackStyleEnabled;
        copy.attackStyleCrit = instance.attackStyleCrit;
        copy.attackStyleKnockback = instance.attackStyleKnockback;
        copy.attackStyleSweep = instance.attackStyleSweep;
        copy.attackStyleColor = instance.attackStyleColor;
        copy.attackStyleRgb = instance.attackStyleRgb;
        copy.attackStyleOverride = instance.attackStyleOverride;
        return copy;
    }

    /** Applies edited values: they take effect immediately and are written back to the TOML file. */
    static void save(ModConfig config) {
        config.targetRgb = parseColor(config.targetColor) & 0xFFFFFF;
        config.hitboxRgb = parseColor(config.hitboxColor) & 0xFFFFFF;
        config.attackIndicatorRgb = parseColor(config.attackIndicatorColor) & 0xFFFFFF;
        config.attackStyleRgb = parseColor(config.attackStyleColor) & 0xFFFFFF;
        config.opacity = clamp01(config.opacity);
        config.hitboxOpacity = clamp01(config.hitboxOpacity);
        if (Float.isNaN(config.hitboxLineWidth)) {
            config.hitboxLineWidth = DEFAULT_HITBOX_WIDTH;
        }
        instance = config;
        write(config);
    }

    private static void write(ModConfig config) {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(toToml(config));
            }
            lastModified = Files.getLastModifiedTime(path).toMillis();
            LOGGER.info("[EasyCrosshairHitboxTint] saved {}", path);
        } catch (Exception e) {
            LOGGER.warn("[EasyCrosshairHitboxTint] could not write {}", path, e);
        }
    }

    // ------------------------------------------------------------------ decisions

    /**
     * The entity the crosshair is currently pointing at <b>within attack range</b>, or {@code null}.
     *
     * <p>Uses the vanilla pick result first, and otherwise casts a short, block-respecting ray whose
     * length is the player's current attack reach (the same {@code AttackRange} data the vanilla
     * attack indicator uses, so weapons with a longer reach work as well). Anything outside that
     * reach is never considered, and walls are never looked through.
     */
    public static Entity aimedEntity(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        Entity picked = targetedEntity(minecraft);
        if (picked != null && isWithinAttackRange(minecraft, picked)) {
            return picked;
        }
        return raycastWithinAttackRange(minecraft);
    }

    /**
     * The ARGB tint of the given crosshair sprite, or {@link #VANILLA} when the sprite must be drawn
     * exactly like vanilla does.
     */
    public int resolveCrosshairColor(Minecraft minecraft, Identifier sprite) {
        if (!enabled || !CROSSHAIR_PATH.equals(sprite.getPath())) {
            return VANILLA;
        }

        int alpha = Math.round(clamp01(opacity) * 255.0F);
        boolean onTarget = isAimingAtTarget(minecraft);
        if (!onTarget && alpha >= 255) {
            // Nothing to change: keep the vanilla inverted crosshair so resource packs stay untouched.
            return VANILLA;
        }

        int rgb = onTarget ? targetRgb : 0xFFFFFF;
        return (alpha << 24) | rgb;
    }

    /**
     * Resolves the attack markers to draw around the crosshair: dashed corner strokes for a critical
     * hit, a {@code ^} for a sprinting knockback attack and a half arc for a sweep attack.
     *
     * <p>The conditions mirror {@code Player#attack}: {@code attackStrengthScale > 0.9},
     * {@code canCriticalAttack}, {@code isSprinting} and {@code isSweepAttack}. Markers are only shown
     * while an entity is aimed at within attack range. Returns {@code null} when nothing is drawn.
     */
    public AttackStyle resolveAttackStyle(Minecraft minecraft) {
        if (!attackStyleEnabled || minecraft == null || minecraft.player == null) {
            return null;
        }
        LocalPlayer player = minecraft.player;
        Entity target = aimedEntity(minecraft);
        if (target == null) {
            return null;
        }

        float attackStrengthScale = player.getAttackStrengthScale(0.5F);
        boolean fullStrength = attackStrengthScale > 0.9F;
        boolean knockback = attackStyleKnockback && player.isSprinting() && fullStrength;
        boolean crit = attackStyleCrit && fullStrength && canCriticalAttack(player, target);
        boolean sweep = attackStyleSweep && isSweepAttack(player, fullStrength, crit, knockback);
        if (!crit && !knockback && !sweep) {
            return null;
        }
        return new AttackStyle(0xFF000000 | attackStyleRgb, crit, knockback, sweep);
    }

    /** Mirrors {@code Player#canCriticalAttack}. */
    private static boolean canCriticalAttack(LocalPlayer player, Entity target) {
        return player.fallDistance > 0.0
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.isMobilityRestricted()
                && !player.isPassenger()
                && target instanceof LivingEntity
                && !player.isSprinting();
    }

    /** Mirrors {@code Player#isSweepAttack}. */
    private static boolean isSweepAttack(LocalPlayer player, boolean fullStrength, boolean crit, boolean knockback) {
        if (!fullStrength || crit || knockback || !player.onGround()) {
            return false;
        }
        double movementSqr = player.getKnownMovement().horizontalDistanceSqr();
        if (movementSqr >= Mth.square(player.getSpeed() * 2.5)) {
            return false;
        }
        return player.getItemInHand(InteractionHand.MAIN_HAND).typeHolder().is(ItemTags.SWORDS);
    }

    /**
     * The ARGB tint of the attack indicator (the cooldown bar under the crosshair), or
     * {@link #VANILLA} while the charge is below the configured threshold.
     *
     * <p>The bar background is never tinted so the progress stays readable.
     */
    public int resolveAttackIndicatorColor(Minecraft minecraft) {
        if (!attackIndicatorEnabled) {
            return VANILLA;
        }
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            return VANILLA;
        }
        float charge = player.getAttackStrengthScale(0.0F);
        if (!(charge >= attackIndicatorThreshold)) {
            return VANILLA;
        }
        // Only while actually aiming at a matching entity within attack range.
        if (!isAimingAtTarget(minecraft)) {
            return VANILLA;
        }
        return 0xFF000000 | attackIndicatorRgb;
    }

    public boolean isAttackIndicatorSprite(Identifier sprite) {
        return sprite.getPath().startsWith(ATTACK_INDICATOR_PREFIX);
    }

    public boolean isAttackIndicatorBackground(Identifier sprite) {
        return ATTACK_INDICATOR_BACKGROUND.equals(sprite.getPath());
    }

    /**
     * Returns the style to draw the given entity's hitbox with, or {@code null} when the vanilla
     * hitbox must be left completely untouched.
     */
    public GizmoStyle resolveHitboxStyle(Entity entity) {
        if (!hitboxEnabled || entity == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!isTargeted(minecraft, entity)) {
            return null;
        }

        float width = hitboxLineWidth;
        if (Float.isNaN(width) || width < MIN_HITBOX_WIDTH) {
            return null;
        }
        if (width > MAX_HITBOX_WIDTH) {
            width = MAX_HITBOX_WIDTH;
        }

        int alpha = Math.round(clamp01(hitboxOpacity) * 255.0F);
        return GizmoStyle.stroke((alpha << 24) | hitboxRgb, width);
    }

    /** Whether the aimed entity's hitbox is drawn even while vanilla's hitbox display (F3+B) is off. */
    public boolean isHitboxAlwaysShow() {
        return hitboxAlwaysShow;
    }

    /** Whether the attack style replaces the vanilla crosshair instead of decorating it. */
    public boolean isAttackStyleOverride() {
        return attackStyleOverride;
    }

    /** Opens the TOML file in the system editor (Notepad on Windows), creating it if needed. */
    public static void openConfigFile() {
        get();
        Path path = filePath().toAbsolutePath();
        try {
            if (!Files.exists(path)) {
                write(instance);
            }
            if (Util.getPlatform() == Util.OS.WINDOWS) {
                new ProcessBuilder("notepad.exe", path.toString()).start();
                return;
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
            }
        } catch (Exception e) {
            LOGGER.warn("[EasyCrosshairHitboxTint] could not open {}", path, e);
        }
    }

    private static AttackRange attackRange(LocalPlayer player) {
        AttackRange range = player.getActiveItem().get(DataComponents.ATTACK_RANGE);
        return range != null ? range : AttackRange.defaultFor(player);
    }

    private static boolean isWithinAttackRange(Minecraft minecraft, Entity entity) {
        AttackRange range = attackRange(minecraft.player);
        return range != null && range.isInRange(minecraft.player, entity.getBoundingBox(), 0.0);
    }

    private static boolean isAimingAtTarget(Minecraft minecraft) {
        Entity entity = aimedEntity(minecraft);
        return entity != null && instance.matches(entity);
    }

    private boolean isTargeted(Minecraft minecraft, Entity entity) {
        return aimedEntity(minecraft) == entity && matches(entity);
    }

    private static Entity targetedEntity(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        if (minecraft.hitResult instanceof EntityHitResult hitResult) {
            return hitResult.getEntity();
        }
        return null;
    }

    private static Entity raycastWithinAttackRange(Minecraft minecraft) {
        // Cheap per-frame memo: the F3+B tint path asks for every rendered entity.
        long now = System.nanoTime();
        if (now - cachedRaycastAt < RAYCAST_CACHE_NANOS) {
            return cachedRaycast;
        }
        cachedRaycastAt = now;
        cachedRaycast = null;

        LocalPlayer player = minecraft.player;
        Entity camera = minecraft.getCameraEntity();
        AttackRange range = attackRange(player);
        if (camera == null || range == null) {
            return null;
        }

        float reach = range.effectiveMaxRange(player);
        if (!(reach > 0.0F) || reach > 64.0F) {
            // Suspicious values: do not apply anything.
            return null;
        }

        Vec3 from = camera.getEyePosition();
        Vec3 view = camera.getViewVector(1.0F);
        Vec3 to = from.add(view.scale(reach));
        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
        if (blockHit.getType() != HitResult.Type.MISS) {
            // Do not look through walls.
            to = blockHit.getLocation();
        }

        AABB searchBox = camera.getBoundingBox().expandTowards(view.scale(reach)).inflate(1.0);
        double rangeSqr = from.distanceToSqr(to);
        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(camera, from, to, searchBox, EntitySelector.CAN_BE_PICKED, rangeSqr);
        if (hitResult != null && range.isInRange(player, hitResult.getLocation())) {
            cachedRaycast = hitResult.getEntity();
        }
        return cachedRaycast;
    }

    private boolean matches(Entity entity) {
        if (targetEntities.isEmpty()) {
            // No restriction configured: any entity is affected.
            return true;
        }

        EntityType<?> type = entity.getType();
        Identifier typeId = EntityType.getKey(type);
        for (String raw : targetEntities) {
            if (raw == null) {
                continue;
            }
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.isEmpty() || "*".equals(entry)) {
                return true;
            }
            if (entry.startsWith("#")) {
                Identifier tagId = parseId(entry.substring(1));
                if (tagId != null && entity.typeHolder().is(TagKey.create(Registries.ENTITY_TYPE, tagId))) {
                    return true;
                }
            } else {
                Identifier id = parseId(entry);
                if (id != null && id.equals(typeId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Identifier parseId(String value) {
        int separator = value.indexOf(':');
        try {
            return separator < 0
                    ? Identifier.fromNamespaceAndPath("minecraft", value)
                    : Identifier.fromNamespaceAndPath(value.substring(0, separator), value.substring(separator + 1));
        } catch (RuntimeException e) {
            LOGGER.warn("[EasyCrosshairHitboxTint] ignoring invalid entity id or tag '{}'", value);
            return null;
        }
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    // ------------------------------------------------------------------ load / save

    private static void reload() {
        Path path = filePath();
        try {
            if (!Files.exists(path)) {
                if (migrateLegacy()) {
                    return;
                }
                if (lastModified < 0L) {
                    write(instance);
                }
                return;
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (modified == lastModified) {
                return;
            }
            // Notepad and other editors may save the file with a UTF-8 BOM.
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            instance = fromJson(parseToml(content));
            lastModified = modified;
            LOGGER.info("[EasyCrosshairHitboxTint] loaded {} (crosshair={}/{}/{}, entities={}, hitbox={}/alwaysShow={}/{}/{}/{}, attackIndicator={}/{}/{}%)",
                    path, instance.enabled, instance.opacity, instance.targetColor, instance.targetEntities,
                    instance.hitboxEnabled, instance.hitboxAlwaysShow, instance.hitboxColor, instance.hitboxOpacity,
                    instance.hitboxLineWidth, instance.attackIndicatorEnabled, instance.attackIndicatorColor,
                    String.format(Locale.ROOT, "%.1f", instance.attackIndicatorThreshold * 100.0F));
        } catch (Exception e) {
            LOGGER.warn("[EasyCrosshairHitboxTint] could not read {}", path, e);
        }
    }

    /** Reads the old JSON config, if present, and rewrites it as TOML. */
    private static boolean migrateLegacy() {
        Path legacy = legacyPath();
        if (!Files.exists(legacy)) {
            return false;
        }
        try {
            String content = Files.readString(legacy, StandardCharsets.UTF_8).trim();
            JsonElement parsed = JsonParser.parseString(content);
            instance = fromJson(parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject());
            write(instance);
            LOGGER.info("[EasyCrosshairHitboxTint] migrated {} to {}", legacy, filePath());
            return true;
        } catch (Exception e) {
            LOGGER.warn("[EasyCrosshairHitboxTint] could not migrate {}", legacy, e);
            return false;
        }
    }

    private static ModConfig fromJson(JsonObject json) {
        ModConfig config = new ModConfig();
        config.enabled = getBoolean(json, "enabled", true);
        config.opacity = clamp01(getFloat(json, "opacity", 1.0F));
        config.targetColor = getString(json, "target_color", DEFAULT_COLOR);
        config.targetRgb = parseColor(config.targetColor) & 0xFFFFFF;

        JsonElement entities = json.get("target_entities");
        if (entities != null && entities.isJsonArray()) {
            for (JsonElement element : entities.getAsJsonArray()) {
                if (element != null && element.isJsonPrimitive()) {
                    config.targetEntities.add(element.getAsString());
                }
            }
        }

        config.hitboxEnabled = getBoolean(json, "hitbox_enabled", false);
        config.hitboxAlwaysShow = getBoolean(json, "hitbox_always_show", false);
        config.hitboxColor = getString(json, "hitbox_color", DEFAULT_COLOR);
        config.hitboxRgb = parseColor(config.hitboxColor) & 0xFFFFFF;
        config.hitboxOpacity = clamp01(getFloat(json, "hitbox_opacity", 1.0F));
        config.hitboxLineWidth = getFloat(json, "hitbox_line_width", DEFAULT_HITBOX_WIDTH);

        config.attackIndicatorEnabled = getBoolean(json, "attack_indicator_enabled", false);
        config.attackIndicatorColor = getString(json, "attack_indicator_color", DEFAULT_COLOR);
        config.attackIndicatorRgb = parseColor(config.attackIndicatorColor) & 0xFFFFFF;
        config.attackIndicatorThreshold = normalizeThreshold(getFloat(json, "attack_indicator_threshold", DEFAULT_ATTACK_THRESHOLD));

        config.attackStyleEnabled = getBoolean(json, "attack_style_enabled", false);
        config.attackStyleCrit = getBoolean(json, "attack_style_crit", true);
        config.attackStyleKnockback = getBoolean(json, "attack_style_knockback", true);
        config.attackStyleSweep = getBoolean(json, "attack_style_sweep", true);
        config.attackStyleColor = getString(json, "attack_style_color", DEFAULT_COLOR);
        config.attackStyleRgb = parseColor(config.attackStyleColor) & 0xFFFFFF;
        config.attackStyleOverride = "override".equalsIgnoreCase(getString(json, "attack_style_mode", "decorate"));
        return config;
    }

    /** Accepts both a ratio (0.885) and a percentage (88.5). */
    static float normalizeThreshold(float value) {
        if (Float.isNaN(value)) {
            return DEFAULT_ATTACK_THRESHOLD;
        }
        if (value > 1.0F) {
            value /= 100.0F;
        }
        return clamp01(value);
    }

    private static String toToml(ModConfig c) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Easy Entity Crosshair & Hitbox Tint\n");
        sb.append("# 淇濆瓨鍚庣害 1 绉掑唴鑷姩鐢熸晥锛涗篃鍙互鍦ㄦ父鎴忓唴 Mod Menu 鐨勯厤缃晫闈㈤噷鍙鍖栦慨鏀广€俓n\n");

        sb.append("# ===== 鍑嗘槦 =====\n");
        sb.append("# 鍑嗘槦鍔熻兘鎬诲紑鍏砛nenabled = ").append(c.enabled).append('\n');
        sb.append("# 鍑嗘槦閫忔槑搴?0.0锛堝叏閫忔槑锛? 1.0锛堜笉閫忔槑锛塡nopacity = ").append(number(c.opacity)).append('\n');
        sb.append("# 鐬勫噯瀹炰綋鏃跺噯鏄熺殑棰滆壊锛?RRGGBB 鎴?#AARRGGBB锛塡ntarget_color = ").append(quote(c.targetColor)).append('\n');
        sb.append("# 鐢熸晥瀹炰綋锛氬疄浣?ID锛坢inecraft:zombie锛夋垨鏍囩锛?minecraft:raiders锛夛紱鐣欑┖ = 浠绘剰瀹炰綋\ntarget_entities = ")
                .append(array(c.targetEntities)).append('\n');

        sb.append("\n# ===== 鍑嗘槦 路 鏀诲嚮鍑嗘槦鏍峰紡锛堢瀯鍑嗗疄浣撴椂锛?=====\n");
        sb.append("# 鏀诲嚮鏍峰紡鎬诲紑鍏砛nattack_style_enabled = ").append(c.attackStyleEnabled).append('\n');
        sb.append("# 鏆村嚮锛氬噯鏄熷洓瑙掑嚭鐜拌櫄鏂滅嚎\nattack_style_crit = ").append(c.attackStyleCrit).append('\n');
        sb.append("# 鐤捐窇鍑婚€€鏀诲嚮锛氬噯鏄熶笂鏂瑰嚭鐜?^\nattack_style_knockback = ").append(c.attackStyleKnockback).append('\n');
        sb.append("# 妯壂鏀诲嚮锛氬噯鏄熶笅鏂瑰嚭鐜板崐寮nattack_style_sweep = ").append(c.attackStyleSweep).append('\n');
        sb.append("# 鏀诲嚮鏍峰紡鐨勯鑹诧紙#RRGGBB 鎴?#AARRGGBB锛塡nattack_style_color = ").append(quote(c.attackStyleColor)).append('\n');
        sb.append("# mode: decorate = draw the markers around the crosshair (default); override = replace the crosshair\nattack_style_mode = ") 
                .append(quote(c.attackStyleOverride ? "override" : "decorate")).append('\n');

        sb.append("\n# ===== 纰版挒绠?=====\n");
        sb.append("# 鐬勫噯瀹炰綋鏃舵槸鍚︾粰瀹冪殑纰版挒绠辨煋鑹瞈nhitbox_enabled = ").append(c.hitboxEnabled).append('\n');
        sb.append("# 鍗充娇鍘熺増 F3+B 纰版挒绠辨樉绀哄叧闂紝涔熺敾鍑虹瀯鍑嗗疄浣撶殑纰版挒绠憋紙榛樿鍏筹級\nhitbox_always_show = ")
                .append(c.hitboxAlwaysShow).append('\n');
        sb.append("# 纰版挒绠遍鑹诧紙#RRGGBB 鎴?#AARRGGBB锛塡nhitbox_color = ").append(quote(c.hitboxColor)).append('\n');
        sb.append("# 纰版挒绠辩嚎鏉￠€忔槑搴?0.0 - 1.0\nhitbox_opacity = ").append(number(c.hitboxOpacity)).append('\n');
        sb.append("# 纰版挒绠辩嚎鏉＄矖缁嗭紝鍘熺増涓?2.5锛屽彲鐢?0.5 - 8.0\nhitbox_line_width = ").append(number(c.hitboxLineWidth)).append('\n');

        sb.append("\n# ===== 鏀诲嚮鎸囩ず鍣紙鍑嗘槦涓嬫柟鐨勬敾鍑诲喎鍗存樉绀猴級 =====\n");
        sb.append("# 鏄惁缁欐敾鍑绘寚绀哄櫒鏌撹壊\nattack_indicator_enabled = ").append(c.attackIndicatorEnabled).append('\n');
        sb.append("# 鏌撹壊棰滆壊锛?RRGGBB 鎴?#AARRGGBB锛塡nattack_indicator_color = ").append(quote(c.attackIndicatorColor)).append('\n');
        sb.append("# 鏀诲嚮鍐峰嵈杈惧埌璇ユ瘮渚嬫椂鎵嶆煋鑹诧細0.885 = 88.5%锛屼篃鍙互鐩存帴鍐?88.5\nattack_indicator_threshold = ")
                .append(number(c.attackIndicatorThreshold)).append('\n');
        return sb.toString();
    }

    private static String number(float value) {
        return Float.toString(value);
    }

    private static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }

    private static String array(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(quote(values.get(i)));
        }
        return sb.append(']').toString();
    }

    // ---- minimal TOML subset parser (flat "key = value" pairs) ----

    private static JsonObject parseToml(String text) {
        JsonObject json = new JsonObject();
        for (String rawLine : text.split("\r?\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty() || line.startsWith("[")) {
                continue;
            }
            int equals = indexOutsideQuotes(line, '=');
            if (equals < 0) {
                continue;
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            json.add(key, parseTomlValue(value));
        }
        return json;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            } else if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static int indexOutsideQuotes(String line, char wanted) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            } else if (c == wanted && !quoted) {
                return i;
            }
        }
        return -1;
    }

    private static JsonElement parseTomlValue(String value) {
        if (value.startsWith("[")) {
            JsonArray array = new JsonArray();
            int end = value.lastIndexOf(']');
            String inner = end > 0 ? value.substring(1, end) : value.substring(1);
            for (String part : splitOutsideQuotes(inner)) {
                String item = part.trim();
                if (!item.isEmpty()) {
                    array.add(unquote(item));
                }
            }
            return array;
        }
        if (value.startsWith("\"")) {
            return new JsonPrimitive(unquote(value));
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return new JsonPrimitive(Boolean.parseBoolean(value));
        }
        String numeric = value.replace("_", "");
        try {
            return new JsonPrimitive(Long.parseLong(numeric));
        } catch (NumberFormatException ignored) {
            try {
                return new JsonPrimitive(Double.parseDouble(numeric));
            } catch (NumberFormatException e) {
                return new JsonPrimitive(unquote(value));
            }
        }
    }

    private static List<String> splitOutsideQuotes(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }
            if (c == ',' && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static String unquote(String value) {
        String result = value.trim();
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        return result.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        try {
            return element == null ? fallback : element.getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float getFloat(JsonObject json, String key, float fallback) {
        JsonElement element = json.get(key);
        try {
            return element == null ? fallback : element.getAsFloat();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String getString(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        try {
            return element == null ? fallback : element.getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Accepts {@code #RRGGBB}, {@code #AARRGGBB} and the same values without the leading {@code #}. */
    static int parseColor(String value) {
        if (value == null) {
            return 0xFF000000 | DEFAULT_RGB;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        try {
            int parsed = (int) Long.parseLong(hex, 16);
            return hex.length() <= 6 ? 0xFF000000 | parsed : parsed;
        } catch (NumberFormatException e) {
            LOGGER.warn("[EasyCrosshairHitboxTint] invalid colour '{}', using #FF0000", value);
            return 0xFF000000 | DEFAULT_RGB;
        }
    }
}
