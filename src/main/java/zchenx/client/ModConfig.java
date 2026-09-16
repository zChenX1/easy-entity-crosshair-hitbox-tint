package zchenx.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
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
 * Configuration of the mod, stored as {@code config/easy_entity_crosshair_hitbox_tint.json}.
 *
 * <p>Only the requested options are exposed: the master switch, the crosshair opacity/colour, the
 * entity filter, and the aimed-entity hitbox tint (switch, colour, opacity, line width). The file is
 * re-read automatically when it is edited on disk.
 */
public final class ModConfig {
    /** Sentinel telling the crosshair mixin to keep the untouched vanilla crosshair. */
    public static final int VANILLA = Integer.MIN_VALUE;

    private static final Logger LOGGER = LoggerFactory.getLogger("easy_entity_crosshair_hitbox_tint");
    private static final String FILE_NAME = "easy_entity_crosshair_hitbox_tint.json";
    private static final String CROSSHAIR_SPRITE_PATH = "hud/crosshair";
    private static final String DEFAULT_TARGET_COLOR = "#FF0000";
    private static final int DEFAULT_TARGET_RGB = 0xFF0000;
    private static final float DEFAULT_HITBOX_WIDTH = 2.5F;
    private static final float MIN_HITBOX_WIDTH = 0.5F;
    private static final float MAX_HITBOX_WIDTH = 8.0F;
    /** Cached "what am I aiming at" result, so the F3+B tint path does not raycast per entity. */
    private static final long RAYCAST_CACHE_NANOS = 10_000_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long CHECK_INTERVAL_MS = 1000L;

    private static ModConfig instance = new ModConfig();
    private static long nextCheck;
    private static long lastModified = -1L;
    private static Entity cachedRaycast;
    private static long cachedRaycastAt;

    private boolean enabled = true;
    private float opacity = 1.0F;
    private String targetColor = DEFAULT_TARGET_COLOR;
    private int targetRgb = DEFAULT_TARGET_RGB;
    private List<String> targetEntities = new ArrayList<>();
    private boolean hitboxEnabled = false;
    private String hitboxColor = DEFAULT_TARGET_COLOR;
    private int hitboxRgb = DEFAULT_TARGET_RGB;
    private float hitboxOpacity = 1.0F;
    private float hitboxLineWidth = DEFAULT_HITBOX_WIDTH;

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

    /**
     * Resolves the ARGB tint of the given crosshair sprite, or {@link #VANILLA} when the sprite must
     * be drawn exactly like vanilla does (inverted blend, no tint).
     */
    public int resolveCrosshairColor(Minecraft minecraft, Identifier sprite) {
        if (!enabled || !CROSSHAIR_SPRITE_PATH.equals(sprite.getPath())) {
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
     * Returns the style to draw the given entity's hitbox with, or {@code null} when the vanilla
     * hitbox must be left completely untouched.
     *
     * <p>Deliberately conservative: anything suspicious (feature off, entity not targeted, invalid
     * line width) results in {@code null}, i.e. no change to the vanilla rendering at all.
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
        int color = (alpha << 24) | hitboxRgb;
        return GizmoStyle.stroke(color, width);
    }

    /** Opens the JSON file in the system editor (Notepad on Windows), creating it if needed. */
    public static void openConfigFile() {
        get();
        Path path = filePath().toAbsolutePath();
        try {
            if (!Files.exists(path)) {
                writeDefault(path);
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

    /**
     * The entity the crosshair is currently pointing at <b>within attack range</b>, or {@code null}.
     *
     * <p>Uses the vanilla pick result first, and otherwise casts a short, block-respecting ray whose
     * length is the player's current attack reach (the same {@code AttackRange} data the vanilla
     * attack indicator uses, so weapons with a longer reach work too). Anything outside that reach is
     * never considered.
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

    private static AttackRange attackRange(LocalPlayer player) {
        AttackRange range = player.getActiveItem().get(DataComponents.ATTACK_RANGE);
        return range != null ? range : AttackRange.defaultFor(player);
    }

    private static boolean isWithinAttackRange(Minecraft minecraft, Entity entity) {
        AttackRange range = attackRange(minecraft.player);
        return range != null && range.isInRange(minecraft.player, entity.getBoundingBox(), 0.0);
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

    private static boolean isAimingAtTarget(Minecraft minecraft) {
        Entity entity = aimedEntity(minecraft);
        return entity != null && instance.matches(entity);
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

    private boolean isTargeted(Minecraft minecraft, Entity entity) {
        return aimedEntity(minecraft) == entity && matches(entity);
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

    private static void reload() {
        Path path = filePath();
        try {
            if (!Files.exists(path)) {
                if (lastModified < 0L) {
                    writeDefault(path);
                }
                return;
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (modified == lastModified) {
                return;
            }
            // Read as text first: Notepad and other editors may save the file with a UTF-8 BOM,
            // which would otherwise make the whole config silently fall back to the defaults.
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            JsonElement parsed = JsonParser.parseString(content.trim());
            instance = fromJson(parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject());
            lastModified = modified;
            LOGGER.info("[EasyCrosshairHitboxTint] loaded {} (crosshair={}, opacity={}, color={}, entities={}, hitbox={}, hitboxColor={}, hitboxOpacity={}, hitboxWidth={})",
                    path, instance.enabled, instance.opacity, instance.targetColor, instance.targetEntities,
                    instance.hitboxEnabled, instance.hitboxColor, instance.hitboxOpacity, instance.hitboxLineWidth);
        } catch (Exception e) {
            LOGGER.warn("[EasyCrosshairHitboxTint] could not read {}", path, e);
        }
    }

    private static void writeDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(new ModConfig().toJson()));
            }
            lastModified = Files.getLastModifiedTime(path).toMillis();
            LOGGER.info("[EasyCrosshairHitboxTint] created default config {}", path);
        } catch (Exception e) {
            LOGGER.warn("[EasyCrosshairHitboxTint] could not create {}", path, e);
        }
    }

    private static ModConfig fromJson(JsonObject json) {
        ModConfig config = new ModConfig();
        config.enabled = getBoolean(json, "enabled", true);
        config.opacity = clamp01(getFloat(json, "opacity", 1.0F));
        config.targetColor = getString(json, "target_color", DEFAULT_TARGET_COLOR);
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
        config.hitboxColor = getString(json, "hitbox_color", DEFAULT_TARGET_COLOR);
        config.hitboxRgb = parseColor(config.hitboxColor) & 0xFFFFFF;
        config.hitboxOpacity = clamp01(getFloat(json, "hitbox_opacity", 1.0F));
        config.hitboxLineWidth = getFloat(json, "hitbox_line_width", DEFAULT_HITBOX_WIDTH);
        return config;
    }

    private JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("opacity", opacity);
        json.addProperty("target_color", targetColor);
        json.add("target_entities", new JsonArray());
        json.addProperty("hitbox_enabled", hitboxEnabled);
        json.addProperty("hitbox_color", hitboxColor);
        json.addProperty("hitbox_opacity", hitboxOpacity);
        json.addProperty("hitbox_line_width", hitboxLineWidth);
        return json;
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
    private static int parseColor(String value) {
        if (value == null) {
            return 0xFF000000 | DEFAULT_TARGET_RGB;
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
            return 0xFF000000 | DEFAULT_TARGET_RGB;
        }
    }
}
