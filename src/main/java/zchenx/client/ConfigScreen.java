package zchenx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The in-game config screen used by Mod Menu.
 *
 * <p>Options are grouped into the three tabs 准星 / 碰撞箱 / 攻击指示器, rendered with the vanilla tab
 * sprites (the same look as the 游戏/世界/更多 tabs). The crosshair tab also shows a live preview of the
 * attack style markers. The layout adapts to the GUI size and the list scrolls with the vanilla
 * scroll bar sprites when it does not fit.
 */
public class ConfigScreen extends Screen {
    private static final int LABEL_WIDTH = 160;
    private static final int CONTROL_WIDTH = 150;
    private static final int TAB_HEIGHT = 24;
    private static final int MARGIN = 8;
    private static final int COLUMN_GAP = 4;
    private static final int MIN_SLOT_HEIGHT = 10;
    private static final int MAX_SLOT_HEIGHT = 20;
    private static final int PREVIEW_HEIGHT = 52;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MIN_HEIGHT = 32;
    private static final String[] TABS = {"准星", "碰撞箱", "攻击指示器"};

    private enum Kind { HEADER, TOGGLE, EDIT, MODE }

    private record Row(Kind kind, String text, String value, int maxLength,
                       BooleanSupplier getter, Consumer<Boolean> setter, Consumer<EditBox> holder) {
        static Row header(String text) {
            return new Row(Kind.HEADER, text, null, 0, null, null, null);
        }

        static Row toggle(String text, BooleanSupplier getter, Consumer<Boolean> setter) {
            return new Row(Kind.TOGGLE, text, null, 0, getter, setter, null);
        }

        static Row edit(String text, String value, int maxLength, Consumer<EditBox> holder) {
            return new Row(Kind.EDIT, text, value, maxLength, null, null, holder);
        }

        static Row mode(String text, BooleanSupplier getter, Consumer<Boolean> setter) {
            return new Row(Kind.MODE, text, null, 0, getter, setter, null);
        }
    }

    /** The vanilla "游戏 / 世界 / 更多" tab strip, drawn exactly like {@code MenuTabBar.MenuTabButton}. */
    private static final class TabBar extends AbstractWidget {
        private static final WidgetSprites SPRITES = new WidgetSprites(
                Identifier.withDefaultNamespace("widget/tab_selected"),
                Identifier.withDefaultNamespace("widget/tab"),
                Identifier.withDefaultNamespace("widget/tab_selected_highlighted"),
                Identifier.withDefaultNamespace("widget/tab_highlighted"));

        private final int tabWidth;
        private final int selected;
        private final IntConsumer onSelect;

        TabBar(int x, int y, int tabWidth, int selected, IntConsumer onSelect) {
            super(x, y, tabWidth * TABS.length, TAB_HEIGHT, Component.literal(TABS[selected]));
            this.tabWidth = tabWidth;
            this.selected = selected;
            this.onSelect = onSelect;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            for (int i = 0; i < TABS.length; i++) {
                int tabX = this.getX() + i * tabWidth;
                boolean isSelected = i == selected;
                boolean hovered = mouseX >= tabX && mouseX < tabX + tabWidth
                        && mouseY >= this.getY() && mouseY < this.getY() + this.getHeight();
                graphics.blitSprite(RenderPipelineTabs.GUI_TEXTURED, SPRITES.get(isSelected, hovered),
                        tabX, this.getY(), tabWidth, this.getHeight());
                if (isSelected && this.active) {
                    // The selected tab is filled with the menu background and gets a focus underline,
                    // just like the vanilla tab bar.
                    Screen.extractMenuBackgroundTexture(graphics, Screen.MENU_BACKGROUND,
                            tabX + 2, this.getY() + 2, 0.0F, 0.0F, tabWidth - 4, this.getHeight() - 2);
                }
                Component label = Component.literal(TABS[i]);
                int color = isSelected ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFFB0B0B0);
                graphics.centeredText(font, label, tabX + tabWidth / 2, this.getY() + (isSelected ? 5 : 7), color);
                if (isSelected && this.active) {
                    int underlineWidth = Math.min(font.width(label), tabWidth - 4);
                    int underlineLeft = tabX + (tabWidth - underlineWidth) / 2;
                    graphics.fill(underlineLeft, this.getY() + this.getHeight() - 2,
                            underlineLeft + underlineWidth, this.getY() + this.getHeight() - 1, 0xFFFFFFFF);
                }
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0 || !this.active) {
                return false;
            }
            if (event.x() < this.getX() || event.x() >= this.getRight()
                    || event.y() < this.getY() || event.y() >= this.getBottom()) {
                return false;
            }
            int index = (int) ((event.x() - this.getX()) / tabWidth);
            if (index >= 0 && index < TABS.length && index != selected) {
                onSelect.accept(index);
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, this.getMessage());
        }
    }

    /** Keeps the render pipeline import out of the nested class for readability. */
    private static final class RenderPipelineTabs {
        static final com.mojang.renderpearl.api.pipeline.RenderPipeline GUI_TEXTURED =
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;

        private RenderPipelineTabs() {
        }
    }

    private final Screen parent;
    private ModConfig config;
    private int page;

    // rows whose value lives in a text box (only the widgets of the visible rows exist)
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

    // scrolling state
    private int scrollRow;
    private int totalRows;
    private int visibleRows;
    private int slotHeight;
    private boolean scrollable;
    private int scrollbarX;
    private int scrollbarTop;
    private int scrollbarHeight;
    private boolean draggingScrollbar;
    private boolean justSaved;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Easy Crosshair & Hitbox Tint"));
        this.parent = parent;
        this.config = ModConfig.copy();
    }

    @Override
    protected void init() {
        clearBoxHolders();
        previewX = -1;
        draggingScrollbar = false;

        int contentTop = 48;
        int contentBottom = this.height - 28;
        int contentHeight = Math.max(20, contentBottom - contentTop);

        previewHeight = (page == 0 && this.width >= 200 && contentHeight >= 130) ? PREVIEW_HEIGHT : 0;
        int viewportHeight = contentHeight - previewHeight;

        List<Row> rows = rows();
        totalRows = rows.size();
        slotHeight = Math.max(MIN_SLOT_HEIGHT, Math.min(MAX_SLOT_HEIGHT, viewportHeight / 6));
        visibleRows = Math.max(1, Math.min(totalRows, viewportHeight / slotHeight));
        scrollable = totalRows > visibleRows;
        scrollRow = Math.max(0, Math.min(scrollRow, totalRows - visibleRows));

        // Columns, leaving room for the scroll bar when it is shown.
        int maxTotal = Math.max(80, this.width - 2 * MARGIN - (scrollable ? SCROLLBAR_WIDTH + 4 : 0));
        int labelWidth = Math.min(LABEL_WIDTH, Math.max(36, (maxTotal - COLUMN_GAP) / 2));
        int controlWidth = Math.min(CONTROL_WIDTH, Math.max(36, maxTotal - COLUMN_GAP - labelWidth));
        int totalWidth = labelWidth + COLUMN_GAP + controlWidth;
        int labelX = (this.width - totalWidth) / 2;
        int controlX = labelX + labelWidth + COLUMN_GAP;

        // the title is drawn centred in extractRenderState
        if (justSaved) {
            // (placeholder handled below)
        }

        int last = Math.min(totalRows, scrollRow + visibleRows);
        for (int i = scrollRow; i < last; i++) {
            buildRow(rows.get(i), labelX, labelWidth, controlX, controlWidth,
                    contentTop + (i - scrollRow) * slotHeight, slotHeight);
        }

        scrollbarX = this.width - MARGIN - SCROLLBAR_WIDTH;
        scrollbarTop = contentTop;
        scrollbarHeight = viewportHeight;

        // ---- vanilla style tab strip ----
        int tabWidth = Math.max(40, (Math.min(400, this.width) - 16) / TABS.length / 2 * 2);
        int tabsWidth = tabWidth * TABS.length;
        int tabX = Math.max(MARGIN, (this.width - tabsWidth) / 2);
        addRenderableWidget(new TabBar(tabX, 20, tabWidth, page, index -> {
            readFields();
            page = index;
            scrollRow = 0;
            requestRebuild();
        }));

        // ---- preview (below the list, only when there is room) ----
        if (previewHeight > 0) {
            previewWidth = Math.min(170, this.width - 2 * MARGIN);
            previewX = this.width / 2 - previewWidth / 2;
            previewY = contentBottom - previewHeight;
        }

        // ---- bottom bar ----
        int buttonWidth = Math.min(110, Math.max(28, (this.width - 2 * MARGIN - 8) / 3));
        int barWidth = buttonWidth * 3 + 8;
        int barX = Math.max(MARGIN, this.width / 2 - barWidth / 2);
        int buttonY = this.height - 22;
        addRenderableWidget(Button.builder(Component.literal("TOML"), b -> ModConfig.openConfigFile())
                .bounds(barX, buttonY, buttonWidth, 18).build());
        saveButton = addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(barX + buttonWidth + 4, buttonY, buttonWidth, 18).build());
        if (justSaved) { saveButton.setMessage(Component.literal("§a已保存")); }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(barX + (buttonWidth + 4) * 2, buttonY, buttonWidth, 18).build());
    }

    /** Rebuilds the widgets on the next client tick (never during an event dispatch). */
    private void requestRebuild() {
        Minecraft minecraft = this.minecraft;
        if (minecraft == null) {
            rebuildWidgets();
        } else {
            minecraft.execute(this::rebuildWidgets);
        }
    }

    private void clearBoxHolders() {
        opacityBox = null;
        targetColorBox = null;
        entitiesBox = null;
        attackStyleColorBox = null;
        hitboxColorBox = null;
        hitboxOpacityBox = null;
        hitboxWidthBox = null;
        attackColorBox = null;
        attackThresholdBox = null;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        switch (page) {
            case 0 -> {
                rows.add(Row.header("准星样式"));
                rows.add(Row.toggle("准星瞄准实体变色", () -> config.enabled, v -> config.enabled = v));
                rows.add(Row.edit("准星透明度 (0.0-1.0)", Float.toString(config.opacity), 16, box -> opacityBox = box));
                rows.add(Row.edit("准星颜色 (#RRGGBB)", config.targetColor, 16, box -> targetColorBox = box));
                rows.add(Row.edit("生效实体 (逗号分隔，空=任意)", String.join(", ", config.targetEntities), 512, box -> entitiesBox = box));
                rows.add(Row.header("攻击准星样式"));
                rows.add(Row.toggle("攻击样式总开关", () -> config.attackStyleEnabled, v -> config.attackStyleEnabled = v));
                rows.add(Row.toggle("暴击 · 四角虚斜线", () -> config.attackStyleCrit, v -> config.attackStyleCrit = v));
                rows.add(Row.toggle("疾跑击退 · 上方 ^", () -> config.attackStyleKnockback, v -> config.attackStyleKnockback = v));
                rows.add(Row.toggle("横扫 · 下方半弧", () -> config.attackStyleSweep, v -> config.attackStyleSweep = v));
                rows.add(Row.edit("攻击样式颜色 (#RRGGBB)", config.attackStyleColor, 16, box -> attackStyleColorBox = box));
                rows.add(Row.mode("攻击样式模式", () -> config.attackStyleOverride, v -> config.attackStyleOverride = v));
            }
            case 1 -> {
                rows.add(Row.header("瞄准碰撞箱"));
                rows.add(Row.toggle("碰撞箱着色", () -> config.hitboxEnabled, v -> config.hitboxEnabled = v));
                rows.add(Row.toggle("无 F3+B 也显示碰撞箱", () -> config.hitboxAlwaysShow, v -> config.hitboxAlwaysShow = v));
                rows.add(Row.edit("碰撞箱颜色 (#RRGGBB)", config.hitboxColor, 16, box -> hitboxColorBox = box));
                rows.add(Row.edit("碰撞箱透明度 (0.0-1.0)", Float.toString(config.hitboxOpacity), 16, box -> hitboxOpacityBox = box));
                rows.add(Row.edit("碰撞箱线宽 (0.5-8.0)", Float.toString(config.hitboxLineWidth), 16, box -> hitboxWidthBox = box));
            }
            default -> {
                rows.add(Row.header("指示器蓄力"));
                rows.add(Row.toggle("攻击指示器染色", () -> config.attackIndicatorEnabled, v -> config.attackIndicatorEnabled = v));
                rows.add(Row.edit("指示器颜色 (#RRGGBB)", config.attackIndicatorColor, 16, box -> attackColorBox = box));
                rows.add(Row.edit("触发阈值 (0.885 = 88.5%)", Float.toString(config.attackIndicatorThreshold), 16, box -> attackThresholdBox = box));
            }
        }
        return rows;
    }

    private void buildRow(Row row, int labelX, int labelWidth, int controlX, int controlWidth, int y, int slotHeight) {
        int height = Math.max(8, slotHeight - 4);
        if (row.kind() == Kind.HEADER) {
            addRenderableWidget(new StringWidget(labelX, y, labelWidth + controlWidth, height, Component.literal("§e§l" + row.text()), this.font));
            return;
        }

        addRenderableWidget(new StringWidget(labelX, y, labelWidth, height, Component.literal(row.text()), this.font));

        switch (row.kind()) {
            case TOGGLE -> addRenderableWidget(Button.builder(toggleLabel(row.getter().getAsBoolean()), b -> {
                readFields();
                row.setter().accept(!row.getter().getAsBoolean());
                rebuildWidgets();
            }).bounds(controlX, y - 1, controlWidth, height).build());
            case MODE -> addRenderableWidget(Button.builder(modeLabel(row.getter().getAsBoolean()), b -> {
                readFields();
                row.setter().accept(!row.getter().getAsBoolean());
                rebuildWidgets();
            }).bounds(controlX, y - 1, controlWidth, height).build());
            case EDIT -> {
                EditBox box = new EditBox(this.font, controlX, y - 1, controlWidth, height, Component.literal(row.text()));
                box.setMaxLength(row.maxLength());
                box.setValue(row.value() == null ? "" : row.value());
                row.holder().accept(box);
                addRenderableWidget(box);
            }
            default -> {
            }
        }
    }

    private static Component toggleLabel(boolean value) {
        return Component.literal(value ? "§a开" : "§c关");
    }

    private static Component modeLabel(boolean override) {
        return Component.literal(override ? "§b覆盖模式" : "§b装饰模式");
    }

    // ------------------------------------------------------------------ scrolling

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollable && scrollY != 0.0) {
            scrollBy(scrollY > 0.0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (scrollable && event.button() == 0
                && event.x() >= scrollbarX - 2 && event.x() <= scrollbarX + SCROLLBAR_WIDTH + 2
                && event.y() >= scrollbarTop && event.y() <= scrollbarTop + scrollbarHeight) {
            draggingScrollbar = true;
            scrollToMouse(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollToMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void scrollBy(int rows) {
        int maxRow = Math.max(0, totalRows - visibleRows);
        int next = Math.max(0, Math.min(maxRow, scrollRow + rows));
        if (next == scrollRow) {
            return;
        }
        readFields();
        scrollRow = next;
        requestRebuild();
    }

    private void scrollToMouse(double mouseY) {
        int maxRow = Math.max(0, totalRows - visibleRows);
        if (maxRow == 0) {
            return;
        }
        int thumbHeight = thumbHeight();
        int travel = scrollbarHeight - thumbHeight;
        double ratio = travel <= 0 ? 0.0 : (mouseY - scrollbarTop - thumbHeight / 2.0) / travel;
        int next = (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * maxRow);
        if (next == scrollRow) {
            return;
        }
        readFields();
        scrollRow = next;
        requestRebuild();
    }

    private int thumbHeight() {
        int height = (int) ((long) scrollbarHeight * visibleRows / Math.max(1, totalRows));
        return Math.max(SCROLLBAR_MIN_HEIGHT, Math.min(scrollbarHeight, height));
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
        justSaved = true;
        requestRebuild();
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
        graphics.centeredText(this.font, this.title, this.width / 2, 5, 0xFFFFFFFF);

        if (scrollable) {
            // Vanilla scroll bar sprites (widget/scroller + widget/scroller_background).
            graphics.blitSprite(RenderPipelineTabs.GUI_TEXTURED, ScrollbarSprites.BACKGROUND,
                    scrollbarX, scrollbarTop, SCROLLBAR_WIDTH, scrollbarHeight);
            int thumbHeight = thumbHeight();
            int maxRow = Math.max(1, totalRows - visibleRows);
            int thumbY = scrollbarTop + (int) ((long) (scrollbarHeight - thumbHeight) * scrollRow / maxRow);
            graphics.blitSprite(RenderPipelineTabs.GUI_TEXTURED,
                    draggingScrollbar ? ScrollbarSprites.HIGHLIGHTED : ScrollbarSprites.THUMB,
                    scrollbarX, thumbY, SCROLLBAR_WIDTH, thumbHeight);
        }

        if (previewX < 0) {
            return;
        }

        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xA0000000);
        border(graphics, previewX, previewY, previewWidth, previewHeight, 0xFF555555);

        int centerX = previewX + previewWidth / 2;
        int centerY = previewY + previewHeight / 2 + 2;
        boolean override = config.attackStyleEnabled && config.attackStyleOverride;
        if (!override) {
            // Vanilla-like crosshair for reference.
            graphics.fill(centerX, centerY - 5, centerX + 1, centerY + 6, 0xFFFFFFFF);
            graphics.fill(centerX - 5, centerY, centerX + 6, centerY + 1, 0xFFFFFFFF);
        }
        if (config.attackStyleEnabled) {
            String raw = attackStyleColorBox != null ? attackStyleColorBox.getValue() : config.attackStyleColor;
            int color = 0xFF000000 | (ModConfig.parseColor(raw) & 0xFFFFFF);
            CrosshairStyles.drawMarkers(graphics, centerX - 7, centerY - 7, 15, 15, color,
                    config.attackStyleCrit, config.attackStyleKnockback, config.attackStyleSweep, override);
        }
    }

    private static final class ScrollbarSprites {
        static final Identifier THUMB = Identifier.withDefaultNamespace("widget/scroller");
        static final Identifier HIGHLIGHTED = Identifier.withDefaultNamespace("widget/scroller");
        static final Identifier BACKGROUND = Identifier.withDefaultNamespace("widget/scroller_background");

        private ScrollbarSprites() {
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
