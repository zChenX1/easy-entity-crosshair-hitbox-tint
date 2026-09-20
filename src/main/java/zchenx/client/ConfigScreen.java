package zchenx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The in-game config screen used by Mod Menu.
 *
 * <p>Options are grouped into the three pages 准星 / 碰撞箱 / 攻击指示器, and the crosshair page shows a
 * live preview of the attack style markers. Only vanilla widgets are used, so no config library is
 * needed, and saving writes the TOML file immediately.
 */
public class ConfigScreen extends Screen {
    private static final int LABEL_WIDTH = 160;
    private static final int CONTROL_WIDTH = 150;
    private static final int TAB_HEIGHT = 18;
    private static final String[] TABS = {"准星", "碰撞箱", "攻击指示器"};

    private final Screen parent;
    private ModConfig config;
    private int page;

    // rows whose value lives in a text box (only the widgets of the current page exist)
    private EditBox opacityBox;
    private EditBox targetColorBox;
    private EditBox entitiesBox;
    private EditBox attackStyleColorBox;
    private EditBox hitboxColorBox;
    private EditBox hitboxOpacityBox;
    private EditBox hitboxWidthBox;
    private EditBox attackColorBox;
    private EditBox attackThresholdBox;

    private Button saveButton;
    private int previewX = -1;
    private int previewY;
    private int previewWidth;
    private int previewHeight;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Easy Crosshair & Hitbox Tint"));
        this.parent = parent;
        this.config = ModConfig.copy();
    }

    @Override
    protected void init() {
        opacityBox = null;
        targetColorBox = null;
        entitiesBox = null;
        attackStyleColorBox = null;
        hitboxColorBox = null;
        hitboxOpacityBox = null;
        hitboxWidthBox = null;
        attackColorBox = null;
        attackThresholdBox = null;
        previewX = -1;

        int labelX = this.width / 2 - 162;
        int controlX = this.width / 2 + 12;

        addRenderableWidget(new StringWidget(labelX, 6, 320, 14, this.title, this.font));

        int tabWidth = Math.min(110, (this.width - 40) / TABS.length);
        int tabX = this.width / 2 - (tabWidth * TABS.length + 4 * (TABS.length - 1)) / 2;
        for (int i = 0; i < TABS.length; i++) {
            int target = i;
            Button tab = addRenderableWidget(Button.builder(Component.literal(TABS[i]), b -> {
                readFields();
                page = target;
                rebuildWidgets();
            }).bounds(tabX + i * (tabWidth + 4), 22, tabWidth, TAB_HEIGHT).build());
            // The selected tab is rendered as an inactive button.
            tab.active = page != i;
        }

        int contentTop = 46;
        int contentBottom = this.height - 30;
        int slots = slotCount();
        int previewReserve = (page == 0 && contentBottom - contentTop >= slots * 12 + 58) ? 58 : 0;
        int slotHeight = Math.max(11, Math.min(20, (contentBottom - contentTop - previewReserve) / Math.max(1, slots)));
        int y = contentTop;

        switch (page) {
            case 0 -> {
                y = header(labelX, y, slotHeight, "准星样式");
                y = toggleRow(labelX, controlX, y, slotHeight, "准星瞄准实体变色", config.enabled, v -> config.enabled = v);
                y = editRow(labelX, controlX, y, slotHeight, "准星透明度 (0.0-1.0)", box -> opacityBox = box, Float.toString(config.opacity), 16);
                y = editRow(labelX, controlX, y, slotHeight, "准星颜色 (#RRGGBB)", box -> targetColorBox = box, config.targetColor, 16);
                y = editRow(labelX, controlX, y, slotHeight, "生效实体 (逗号分隔，空=任意)", box -> entitiesBox = box, String.join(", ", config.targetEntities), 512);
                y += 2;
                y = header(labelX, y, slotHeight, "攻击准星样式");
                y = toggleRow(labelX, controlX, y, slotHeight, "攻击样式总开关", config.attackStyleEnabled, v -> config.attackStyleEnabled = v);
                y = toggleRow(labelX, controlX, y, slotHeight, "暴击 · 四角虚斜线", config.attackStyleCrit, v -> config.attackStyleCrit = v);
                y = toggleRow(labelX, controlX, y, slotHeight, "疾跑击退 · 上方 ^", config.attackStyleKnockback, v -> config.attackStyleKnockback = v);
                y = toggleRow(labelX, controlX, y, slotHeight, "横扫 · 下方半弧", config.attackStyleSweep, v -> config.attackStyleSweep = v);
                editRow(labelX, controlX, y, slotHeight, "攻击样式颜色 (#RRGGBB)", box -> attackStyleColorBox = box, config.attackStyleColor, 16);
            }
            case 1 -> {
                y = header(labelX, y, slotHeight, "瞄准碰撞箱");
                y = toggleRow(labelX, controlX, y, slotHeight, "碰撞箱着色", config.hitboxEnabled, v -> config.hitboxEnabled = v);
                y = toggleRow(labelX, controlX, y, slotHeight, "无 F3+B 也显示碰撞箱", config.hitboxAlwaysShow, v -> config.hitboxAlwaysShow = v);
                y = editRow(labelX, controlX, y, slotHeight, "碰撞箱颜色 (#RRGGBB)", box -> hitboxColorBox = box, config.hitboxColor, 16);
                y = editRow(labelX, controlX, y, slotHeight, "碰撞箱透明度 (0.0-1.0)", box -> hitboxOpacityBox = box, Float.toString(config.hitboxOpacity), 16);
                editRow(labelX, controlX, y, slotHeight, "碰撞箱线宽 (0.5-8.0)", box -> hitboxWidthBox = box, Float.toString(config.hitboxLineWidth), 16);
            }
            default -> {
                y = header(labelX, y, slotHeight, "指示器蓄力");
                y = toggleRow(labelX, controlX, y, slotHeight, "攻击指示器染色", config.attackIndicatorEnabled, v -> config.attackIndicatorEnabled = v);
                y = editRow(labelX, controlX, y, slotHeight, "指示器颜色 (#RRGGBB)", box -> attackColorBox = box, config.attackIndicatorColor, 16);
                editRow(labelX, controlX, y, slotHeight, "触发阈值 (0.885 = 88.5%)", box -> attackThresholdBox = box, Float.toString(config.attackIndicatorThreshold), 16);
            }
        }

        if (previewReserve > 0) {
            previewWidth = Math.min(170, Math.max(110, this.width / 3));
            previewHeight = 52;
            previewX = this.width / 2 - previewWidth / 2;
            previewY = contentBottom - previewHeight;
        }

        int buttonY = this.height - 24;
        int third = Math.min(120, this.width / 3 - 6);
        int baseX = this.width / 2 - (third * 3 + 8) / 2;
        addRenderableWidget(Button.builder(Component.literal("打开 TOML"), b -> ModConfig.openConfigFile())
                .bounds(baseX, buttonY, third, 18).build());
        saveButton = addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(baseX + third + 4, buttonY, third, 18).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(baseX + (third + 4) * 2, buttonY, third, 18).build());
    }

    private int slotCount() {
        return switch (page) {
            case 0 -> 11;
            case 1 -> 6;
            default -> 4;
        };
    }

    private int header(int labelX, int y, int slotHeight, String title) {
        addRenderableWidget(new StringWidget(labelX, y, LABEL_WIDTH, slotHeight - 4, Component.literal("§e§l" + title), this.font));
        return y + slotHeight;
    }

    private int toggleRow(int labelX, int controlX, int y, int slotHeight, String label, boolean value, Consumer<Boolean> setter) {
        addRenderableWidget(new StringWidget(labelX, y, LABEL_WIDTH, slotHeight - 4, Component.literal(label), this.font));
        addRenderableWidget(Button.builder(toggleLabel(value), b -> {
            readFields();
            setter.accept(!value);
            rebuildWidgets();
        }).bounds(controlX, y - 1, CONTROL_WIDTH, slotHeight - 4).build());
        return y + slotHeight;
    }

    private int editRow(int labelX, int controlX, int y, int slotHeight, String label, Consumer<EditBox> holder, String value, int maxLength) {
        addRenderableWidget(new StringWidget(labelX, y, LABEL_WIDTH, slotHeight - 4, Component.literal(label), this.font));
        EditBox box = new EditBox(this.font, controlX, y - 1, CONTROL_WIDTH, slotHeight - 4, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        holder.accept(box);
        addRenderableWidget(box);
        return y + slotHeight;
    }

    private static Component toggleLabel(boolean value) {
        return Component.literal(value ? "§a开" : "§c关");
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
        if (attackStyleColorBox != null) {
            config.attackStyleColor = attackStyleColorBox.getValue().trim();
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
        rebuildWidgets();
        if (saveButton != null) {
            saveButton.setMessage(Component.literal("§a已保存"));
        }
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (previewX < 0) {
            return;
        }

        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xA0000000);
        border(graphics, previewX, previewY, previewWidth, previewHeight, 0xFF555555);

        int centerX = previewX + previewWidth / 2;
        int centerY = previewY + previewHeight / 2 + 2;
        // Vanilla-like crosshair for reference.
        graphics.fill(centerX, centerY - 5, centerX + 1, centerY + 6, 0xFFFFFFFF);
        graphics.fill(centerX - 5, centerY, centerX + 6, centerY + 1, 0xFFFFFFFF);
        // Attack style markers, using the values currently typed in the boxes.
        if (config.attackStyleEnabled) {
            String raw = attackStyleColorBox != null ? attackStyleColorBox.getValue() : config.attackStyleColor;
            int color = 0xFF000000 | (ModConfig.parseColor(raw) & 0xFFFFFF);
            CrosshairStyles.drawOverlays(graphics, centerX - 7, centerY - 7, 15, 15, color,
                    config.attackStyleCrit, config.attackStyleKnockback, config.attackStyleSweep);
        }
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    public void onClose() {
        Minecraft minecraft = this.minecraft;
        if (minecraft != null) {
            minecraft.setScreenAndShow(this.parent);
        }
    }
}
