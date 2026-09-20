package zchenx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game config screen used by Mod Menu (and reachable from the mod list).
 *
 * <p>It only uses vanilla widgets, so it needs no config library: every option of the TOML file can
 * be edited here, and saving writes the file back immediately. The button at the bottom still opens
 * the raw TOML in the system editor for advanced editing.
 */
public class ConfigScreen extends Screen {
    private static final int ROWS = 12;
    private static final int LABEL_WIDTH = 168;
    private static final int CONTROL_WIDTH = 150;

    private final Screen parent;
    private ModConfig config;

    private Button crosshairButton;
    private Button hitboxButton;
    private Button alwaysShowButton;
    private Button attackIndicatorButton;
    private Button saveButton;

    private EditBox opacityBox;
    private EditBox targetColorBox;
    private EditBox entitiesBox;
    private EditBox hitboxColorBox;
    private EditBox hitboxOpacityBox;
    private EditBox hitboxWidthBox;
    private EditBox attackColorBox;
    private EditBox attackThresholdBox;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Easy Crosshair & Hitbox Tint"));
        this.parent = parent;
        this.config = ModConfig.copy();
    }

    @Override
    protected void init() {
        int labelX = this.width / 2 - 165;
        int controlX = this.width / 2 + 15;
        // Adaptive layout: keep all rows above the two button rows even on small GUI scales.
        int rowHeight = Math.max(14, Math.min(22, (this.height - 76) / ROWS));
        int gap = rowHeight >= 20 ? 6 : 2;
        int y = 24;

        addRenderableWidget(new StringWidget(labelX, 6, 330, 16, this.title, this.font));

        addRenderableWidget(new StringWidget(labelX, y, LABEL_WIDTH, rowHeight - 4, Component.literal("准星瞄准实体变色"), this.font));
        crosshairButton = addRenderableWidget(Button.builder(toggleLabel(config.enabled), b -> {
            readFields();
            config.enabled = !config.enabled;
            rebuildWidgets();
        }).bounds(controlX, y - 1, CONTROL_WIDTH, rowHeight - 4).build());
        y += rowHeight;

        opacityBox = addEditBox(labelX, controlX, y, rowHeight, "准星透明度 (0.0-1.0)", Float.toString(config.opacity), 16);
        y += rowHeight;

        targetColorBox = addEditBox(labelX, controlX, y, rowHeight, "准星颜色 (#RRGGBB)", config.targetColor, 16);
        y += rowHeight;

        entitiesBox = addEditBox(labelX, controlX, y, rowHeight, "生效实体 (逗号分隔，空=任意)", String.join(", ", config.targetEntities), 512);
        y += rowHeight + gap;

        addRenderableWidget(new StringWidget(labelX, y, LABEL_WIDTH, rowHeight - 4, Component.literal("碰撞箱着色"), this.font));
        hitboxButton = addRenderableWidget(Button.builder(toggleLabel(config.hitboxEnabled), b -> {
            readFields();
            config.hitboxEnabled = !config.hitboxEnabled;
            rebuildWidgets();
        }).bounds(controlX, y - 1, CONTROL_WIDTH, rowHeight - 4).build());
        y += rowHeight;

        addRenderableWidget(new StringWidget(labelX, y, LABEL_WIDTH, rowHeight - 4, Component.literal("无 F3+B 也显示碰撞箱"), this.font));
        alwaysShowButton = addRenderableWidget(Button.builder(toggleLabel(config.hitboxAlwaysShow), b -> {
            readFields();
            config.hitboxAlwaysShow = !config.hitboxAlwaysShow;
            rebuildWidgets();
        }).bounds(controlX, y - 1, CONTROL_WIDTH, rowHeight - 4).build());
        y += rowHeight;

        hitboxColorBox = addEditBox(labelX, controlX, y, rowHeight, "碰撞箱颜色 (#RRGGBB)", config.hitboxColor, 16);
        y += rowHeight;

        hitboxOpacityBox = addEditBox(labelX, controlX, y, rowHeight, "碰撞箱透明度 (0.0-1.0)", Float.toString(config.hitboxOpacity), 16);
        y += rowHeight;

        hitboxWidthBox = addEditBox(labelX, controlX, y, rowHeight, "碰撞箱线宽 (0.5-8.0)", Float.toString(config.hitboxLineWidth), 16);
        y += rowHeight + gap;

        addRenderableWidget(new StringWidget(labelX, y, LABEL_WIDTH, rowHeight - 4, Component.literal("攻击指示器染色"), this.font));
        attackIndicatorButton = addRenderableWidget(Button.builder(toggleLabel(config.attackIndicatorEnabled), b -> {
            readFields();
            config.attackIndicatorEnabled = !config.attackIndicatorEnabled;
            rebuildWidgets();
        }).bounds(controlX, y - 1, CONTROL_WIDTH, rowHeight - 4).build());
        y += rowHeight;

        attackColorBox = addEditBox(labelX, controlX, y, rowHeight, "攻击指示器颜色 (#RRGGBB)", config.attackIndicatorColor, 16);
        y += rowHeight;

        attackThresholdBox = addEditBox(labelX, controlX, y, rowHeight, "触发阈值 (0.885 = 88.5%)", Float.toString(config.attackIndicatorThreshold), 16);

        int buttonY = this.height - 24;
        int half = Math.min(160, this.width / 2 - 10);
        saveButton = addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(this.width / 2 - half - 5, buttonY, half, 18).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width / 2 + 5, buttonY, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("打开 TOML 文件"), b -> ModConfig.openConfigFile())
                .bounds(this.width / 2 - half / 2, buttonY - 22, half, 18).build());
    }

    private EditBox addEditBox(int labelX, int controlX, int y, int rowHeight, String label, String value, int maxLength) {
        addRenderableWidget(new StringWidget(labelX, y, LABEL_WIDTH, rowHeight - 4, Component.literal(label), this.font));
        EditBox box = new EditBox(this.font, controlX, y - 1, CONTROL_WIDTH, rowHeight - 4, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        return addRenderableWidget(box);
    }

    private static Component toggleLabel(boolean value) {
        return Component.literal(value ? "开" : "关");
    }

    private void readFields() {
        if (opacityBox != null) {
            config.opacity = parseFloat(opacityBox.getValue(), config.opacity);
        }
        if (targetColorBox != null) {
            config.targetColor = targetColorBox.getValue().trim();
        }
        if (entitiesBox != null) {
            config.targetEntities = parseList(entitiesBox.getValue());
        }
        if (hitboxColorBox != null) {
            config.hitboxColor = hitboxColorBox.getValue().trim();
        }
        if (hitboxOpacityBox != null) {
            config.hitboxOpacity = parseFloat(hitboxOpacityBox.getValue(), config.hitboxOpacity);
        }
        if (hitboxWidthBox != null) {
            config.hitboxLineWidth = parseFloat(hitboxWidthBox.getValue(), config.hitboxLineWidth);
        }
        if (attackColorBox != null) {
            config.attackIndicatorColor = attackColorBox.getValue().trim();
        }
        if (attackThresholdBox != null) {
            config.attackIndicatorThreshold = ModConfig.normalizeThreshold(parseFloat(attackThresholdBox.getValue(), config.attackIndicatorThreshold));
        }
    }

    private void save() {
        readFields();
        ModConfig.save(config);
        config = ModConfig.copy();
        if (saveButton != null) {
            saveButton.setMessage(Component.literal("已保存"));
        }
        rebuildWidgets();
    }

    private static List<String> parseList(String value) {
        List<String> entries = new ArrayList<>();
        for (String part : value.split("[,;]")) {
            String entry = part.trim();
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    @Override
    public void onClose() {
        Minecraft minecraft = this.minecraft;
        if (minecraft != null) {
            minecraft.setScreenAndShow(this.parent);
        }
    }
}
