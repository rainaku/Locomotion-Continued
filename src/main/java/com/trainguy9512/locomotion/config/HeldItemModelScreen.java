package com.trainguy9512.locomotion.config;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Live 3D held-item model editor using the in-world first-person renderer. */
public final class HeldItemModelScreen extends Screen {
    private enum Tab { POSITION, ROTATION, PIVOT }

    private enum SliderKind {
        PIXEL(-64f, 128f, 0.1f, 10f, " px", "position.description"),
        ANGLE(-180f, 360f, 0.5f, 2f, " deg", "rotation.description"),
        SCALE(0.05f, 7.95f, 0.01f, 100f, "x", "scale.description");

        final float min;
        final float span;
        final float step;
        final float rounding;
        final String unit;
        final String descriptionKey;

        SliderKind(float min, float span, float step, float rounding, String unit, String descriptionKey) {
            this.min = min;
            this.span = span;
            this.step = step;
            this.rounding = rounding;
            this.unit = unit;
            this.descriptionKey = descriptionKey;
        }

        float round(double value) {
            return (float) (Math.round(Math.clamp(value, min, min + span) * rounding) / rounding);
        }
    }

    private record AxisRow(
            String key,
            SliderKind kind,
            String tooltipKey,
            float defaultValue,
            Function<ItemModelTransform, Float> getter,
            BiConsumer<ItemModelTransform, Float> setter,
            ModelSlider slider,
            Button resetButton
    ) {
        void sync(HeldItemModelEditor editor) {
            slider.syncFromModel();
            float current = getter.apply(editor.current());
            resetButton.active = editor.canEdit() && Math.abs(current - defaultValue) > 1e-4f;
        }
    }

    private static final int SURFACE_BG = 0xEC10151E;
    private static final int HEADER_BG = 0xFF171F2C;
    private static final int BORDER_COLOR = 0xFF283548;
    private static final int SLOT_BG = 0xFF161E2B;
    private static final int SLOT_HOVER_BG = 0xFF212D40;
    private static final int SLOT_ACTIVE_BG = 0xFF1E324C;
    private static final int ACCENT_COLOR = 0xFF4CA6FF;
    private static final int TEXT_PRIMARY = 0xFFF1F5F9;
    private static final int TEXT_MUTED = 0xFF94A3B8;

    private final Screen parent;
    private final LocomotionConfig config;
    private final HeldItemModelEditor editor;
    private final List<AxisRow> axisRows = new ArrayList<>();
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    private String selectedItem;
    private Tab tab = Tab.POSITION;
    private Boolean manualPanelOnRight = null;
    private boolean panelOnRight;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int hotbarStripX;
    private int hotbarStripY;
    private CameraType previousCamera;
    private boolean hudWasHidden;
    private boolean previewStarted;
    private boolean draggingPreview;
    private int dragButton;

    public HeldItemModelScreen(Screen parent, LocomotionConfig config) {
        super(Component.translatable("locomotion.config.held_models.title"));
        this.parent = parent;
        this.config = config;
        this.editor = new HeldItemModelEditor(config.data().firstPersonPlayer.heldItemModels);
        if (minecraft.player != null && minecraft.player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                && !minecraft.player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
            hand = InteractionHand.OFF_HAND;
        }
    }

    @Override
    public void added() {
        config.beginHeldItemPreview(editor.preview());
        if (minecraft.level != null && !previewStarted) {
            previousCamera = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            hudWasHidden = minecraft.gui.hud.isHidden();
            if (!hudWasHidden) {
                minecraft.gui.hud.toggle();
            }
            previewStarted = true;
        }
    }

    @Override
    public void removed() {
        config.endHeldItemPreview(editor.preview());
        if (previewStarted) {
            minecraft.options.setCameraType(previousCamera);
            if (minecraft.gui.hud.isHidden() != hudWasHidden) {
                minecraft.gui.hud.toggle();
            }
            previewStarted = false;
        }
    }

    @Override
    protected void init() {
        axisRows.clear();
        updateTarget();
        panelWidth = Math.min(236, Math.max(196, width / 2 - 12));
        panelHeight = Math.min(242, height - 12);
        panelY = Math.max(6, (height - panelHeight) / 2);
        panelX = panelOnRight ? width - panelWidth - 8 : 8;

        int x = panelX + 8;
        int w = panelWidth - 16;
        int half = (w - 4) / 2;
        int third = (w - 8) / 3;

        // Header controls: Help [?] and Dock Side Flip [⇄]
        Button helpBtn = addRenderableWidget(Button.builder(Component.literal("?"), ignored -> {})
                .bounds(panelX + panelWidth - 42, panelY + 4, 16, 16)
                .tooltip(Tooltip.create(Component.translatable("locomotion.config.held_models.editor.help")))
                .build());
        helpBtn.active = false;

        addRenderableWidget(Button.builder(Component.literal(panelOnRight ? "<" : ">"), ignored -> {
            manualPanelOnRight = !panelOnRight;
            rebuildWidgets();
        }).bounds(panelX + panelWidth - 22, panelY + 4, 16, 16).build());

        // Row 1: Scope Mode (All Items vs This Item) at panelY + 48
        int scopeY = panelY + 48;
        var globalBtn = button("mode.global", x, scopeY, half, () -> setMode(HeldItemModelSettings.Mode.GLOBAL));
        globalBtn.active = editor.preview().mode != HeldItemModelSettings.Mode.GLOBAL;
        var specificBtn = button("mode.specific", x + half + 4, scopeY, w - half - 4, () -> setMode(HeldItemModelSettings.Mode.SPECIFIC));
        specificBtn.active = selectedItem != null && editor.preview().mode != HeldItemModelSettings.Mode.SPECIFIC;

        // Row 2: Hotbar & Hand selector at panelY + 70
        hotbarStripX = x;
        hotbarStripY = panelY + 70;
        if (minecraft.player == null) {
            var mainBtn = addRenderableWidget(Button.builder(
                    Component.translatable("locomotion.config.held_models.editor.main_hand"),
                    ignored -> { hand = InteractionHand.MAIN_HAND; rebuildWidgets(); }
            ).bounds(x, hotbarStripY, half, 18).build());
            mainBtn.active = hand != InteractionHand.MAIN_HAND;

            var offBtn = addRenderableWidget(Button.builder(
                    Component.translatable("locomotion.config.held_models.editor.off_hand"),
                    ignored -> { hand = InteractionHand.OFF_HAND; rebuildWidgets(); }
            ).bounds(x + half + 4, hotbarStripY, w - half - 4, 18).build());
            offBtn.active = hand != InteractionHand.OFF_HAND;
        }

        // Row 3: Transform Tab (Position | Rotation | Pivot) at panelY + 94
        int tabY = panelY + 94;
        var posBtn = button("editor.position", x, tabY, third, () -> { tab = Tab.POSITION; rebuildWidgets(); });
        posBtn.active = tab != Tab.POSITION;
        var rotBtn = button("editor.rotation", x + third + 4, tabY, third, () -> { tab = Tab.ROTATION; rebuildWidgets(); });
        rotBtn.active = tab != Tab.ROTATION;
        var pivBtn = button("editor.pivot", x + (third + 4) * 2, tabY, w - (third + 4) * 2, () -> { tab = Tab.PIVOT; rebuildWidgets(); });
        pivBtn.active = tab != Tab.PIVOT;

        // Row 4: Axis Sliders with [-], [+], and per-axis [↺] reset
        SliderKind axisKind = tab == Tab.ROTATION ? SliderKind.ANGLE : SliderKind.PIXEL;
        String tooltipKey = tab == Tab.PIVOT ? "pivot.description" : axisKind.descriptionKey;
        int sliderStart = panelY + 116;

        addAxisControl(x, sliderStart, w, axisKey("x"), axisKind, tooltipKey, 0f,
                t -> switch (tab) { case POSITION -> t.positionX; case ROTATION -> t.rotationX; case PIVOT -> t.pivotX; },
                (t, value) -> { switch (tab) { case POSITION -> t.positionX = value; case ROTATION -> t.rotationX = value; case PIVOT -> t.pivotX = value; } });

        addAxisControl(x, sliderStart + 21, w, axisKey("y"), axisKind, tooltipKey, 0f,
                t -> switch (tab) { case POSITION -> t.positionY; case ROTATION -> t.rotationY; case PIVOT -> t.pivotY; },
                (t, value) -> { switch (tab) { case POSITION -> t.positionY = value; case ROTATION -> t.rotationY = value; case PIVOT -> t.pivotY = value; } });

        addAxisControl(x, sliderStart + 42, w, axisKey("z"), axisKind, tooltipKey, 0f,
                t -> switch (tab) { case POSITION -> t.positionZ; case ROTATION -> t.rotationZ; case PIVOT -> t.pivotZ; },
                (t, value) -> { switch (tab) { case POSITION -> t.positionZ = value; case ROTATION -> t.rotationZ = value; case PIVOT -> t.pivotZ = value; } });

        addAxisControl(x, sliderStart + 65, w, "scale", SliderKind.SCALE, SliderKind.SCALE.descriptionKey, 1f,
                t -> t.scale, (t, value) -> t.scale = value);

        // Footer Row: Reset All / Inherit | Save | Cancel
        int footerY = panelY + panelHeight - 24;
        int resetW = Math.max(54, w / 3 - 2);
        int actionW = (w - resetW - 8) / 2;

        var resetAll = button(editor.preview().mode == HeldItemModelSettings.Mode.GLOBAL ? "editor.reset" : "editor.inherit",
                x, footerY, resetW, () -> { editor.reset(); syncControls(); });
        resetAll.active = editor.canEdit();

        addRenderableWidget(Button.builder(Component.translatable("locomotion.config.held_models.editor.save"), ignored -> {
            config.data().firstPersonPlayer.heldItemModels = editor.snapshot();
            config.save();
            onClose();
        }).bounds(x + resetW + 4, footerY, actionW, 18).build());

        button("editor.cancel", x + resetW + actionW + 8, footerY, w - resetW - actionW - 8, this::onClose);
        syncControls();
    }

    private String axisKey(String axis) {
        return switch (tab) {
            case POSITION -> "position_" + axis;
            case ROTATION -> "rotation_" + axis;
            case PIVOT -> "pivot_" + axis;
        };
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.translatable("locomotion.config.held_models." + key), ignored -> action.run())
                .bounds(x, y, width, 18).build());
    }

    private void addAxisControl(int x, int y, int width, String key, SliderKind kind, String tooltipKey, float defaultVal,
                                Function<ItemModelTransform, Float> getter, BiConsumer<ItemModelTransform, Float> setter) {
        int btnW = 14;
        int resetW = 16;
        int sliderW = width - (btnW * 2) - resetW - 6;

        Button minusBtn = addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
            editor.edit(t -> setter.accept(t, kind.round(getter.apply(t) - kind.step)));
            syncControls();
        }).bounds(x, y, btnW, 18).build());
        minusBtn.active = editor.canEdit();

        var slider = new ModelSlider(x + btnW + 2, y, sliderW, key, kind, getter, value -> {
            editor.edit(transform -> setter.accept(transform, value));
            syncControls();
        });
        slider.active = editor.canEdit();
        slider.setTooltip(Tooltip.create(Component.translatable("locomotion.config.held_models." + tooltipKey)));
        addRenderableWidget(slider);

        Button plusBtn = addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
            editor.edit(t -> setter.accept(t, kind.round(getter.apply(t) + kind.step)));
            syncControls();
        }).bounds(x + btnW + sliderW + 4, y, btnW, 18).build());
        plusBtn.active = editor.canEdit();

        Button resetBtn = addRenderableWidget(Button.builder(Component.literal("R"), ignored -> {
            editor.edit(t -> setter.accept(t, defaultVal));
            syncControls();
        }).bounds(x + width - resetW, y, resetW, 18)
                .tooltip(Tooltip.create(Component.translatable("locomotion.config.held_models.editor.reset")))
                .build());

        axisRows.add(new AxisRow(key, kind, tooltipKey, defaultVal, getter, setter, slider, resetBtn));
    }

    private void syncControls() {
        for (AxisRow row : axisRows) {
            row.sync(editor);
        }
    }

    private void setMode(HeldItemModelSettings.Mode mode) {
        editor.setMode(mode);
        rebuildWidgets();
    }

    private ItemStack activeItemStack() {
        if (minecraft.player == null) return ItemStack.EMPTY;
        return minecraft.player.getItemInHand(hand);
    }

    private String itemInHand() {
        ItemStack stack = activeItemStack();
        if (stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private boolean isLeftArm() {
        if (minecraft.player == null) return false;
        HumanoidArm side = hand == InteractionHand.MAIN_HAND ? minecraft.player.getMainArm() : minecraft.player.getMainArm().getOpposite();
        return side == HumanoidArm.LEFT;
    }

    private void updateTarget() {
        selectedItem = itemInHand();
        editor.selectItem(selectedItem);
        if (manualPanelOnRight != null) {
            panelOnRight = manualPanelOnRight;
        } else if (minecraft.player != null) {
            panelOnRight = isLeftArm();
        }
    }

    private boolean isInPanel(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= panelY && mouseY < panelY + panelHeight;
    }

    @Override
    public void tick() {
        if (!Objects.equals(selectedItem, itemInHand())) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (minecraft.player != null && event.button() == 0 && handleHotbarStripClick(event.x(), event.y())) {
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (!isInPanel(event.x(), event.y()) && editor.canEdit() && (event.button() == 0 || event.button() == 1)) {
            draggingPreview = true;
            dragButton = event.button();
            return true;
        }
        return false;
    }

    private boolean handleHotbarStripClick(double mouseX, double mouseY) {
        if (mouseY < hotbarStripY || mouseY >= hotbarStripY + 20) {
            return false;
        }
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < 9; i++) {
            int sx = hotbarStripX + i * 21;
            if (mouseX >= sx && mouseX < sx + 20) {
                inv.setSelectedSlot(i);
                hand = InteractionHand.MAIN_HAND;
                rebuildWidgets();
                return true;
            }
        }
        int offX = hotbarStripX + 9 * 21 + 5;
        if (mouseX >= offX && mouseX < offX + 20) {
            hand = InteractionHand.OFF_HAND;
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingPreview && event.button() == dragButton) {
            draggingPreview = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingPreview && editor.canEdit()) {
            boolean zAxis = dragButton == 1 || event.hasShiftDown();
            float mirror = isLeftArm() ? -1f : 1f;
            editor.edit(t -> {
                switch (tab) {
                    case POSITION -> {
                        if (zAxis) {
                            t.positionZ = SliderKind.PIXEL.round(t.positionZ + dragY * 0.2);
                        } else {
                            t.positionX = SliderKind.PIXEL.round(t.positionX + dragX * 0.2 * mirror);
                            t.positionY = SliderKind.PIXEL.round(t.positionY - dragY * 0.2);
                        }
                    }
                    case ROTATION -> {
                        if (zAxis) {
                            t.rotationZ = SliderKind.ANGLE.round(t.rotationZ + dragX * 0.6 * mirror);
                        } else {
                            t.rotationY = SliderKind.ANGLE.round(t.rotationY + dragX * 0.6 * mirror);
                            t.rotationX = SliderKind.ANGLE.round(t.rotationX + dragY * 0.6);
                        }
                    }
                    case PIVOT -> {
                        if (zAxis) {
                            t.pivotZ = SliderKind.PIXEL.round(t.pivotZ + dragY * 0.2);
                        } else {
                            t.pivotX = SliderKind.PIXEL.round(t.pivotX + dragX * 0.2 * mirror);
                            t.pivotY = SliderKind.PIXEL.round(t.pivotY - dragY * 0.2);
                        }
                    }
                }
            });
            syncControls();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isInPanel(mouseX, mouseY) && editor.canEdit() && scrollY != 0) {
            editor.edit(t -> t.scale = SliderKind.SCALE.round(t.scale + scrollY * 0.05));
            syncControls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.player != null && event.key() >= 49 && event.key() <= 57) {
            minecraft.player.getInventory().setSelectedSlot(event.key() - 49);
            hand = InteractionHand.MAIN_HAND;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }
        // Main dock surface
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, SURFACE_BG);
        // Header bar
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 24, HEADER_BG);
        graphics.fill(panelX, panelY + 24, panelX + panelWidth, panelY + 25, BORDER_COLOR);
        // Crisp 1px outer border
        drawOutline(graphics, panelX, panelY, panelWidth, panelHeight, BORDER_COLOR);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Title in header
        graphics.text(font, title, panelX + 8, panelY + 8, TEXT_PRIMARY);

        // Active item row (Icon + Display Name)
        ItemStack currentStack = activeItemStack();
        int itemRowY = panelY + 28;
        if (!currentStack.isEmpty()) {
            graphics.fakeItem(currentStack, panelX + 8, itemRowY);
            graphics.enableScissor(panelX + 28, itemRowY, panelX + panelWidth - 8, itemRowY + 18);
            graphics.text(font, currentStack.getHoverName(), panelX + 28, itemRowY + 4, TEXT_PRIMARY);
            graphics.disableScissor();
        } else {
            graphics.text(font, Component.translatable("locomotion.config.held_models.editor.empty_hand"),
                    panelX + 8, itemRowY + 4, TEXT_MUTED);
        }

        // Visual 9-slot Hotbar + Offhand strip when in-game
        if (minecraft.player != null) {
            renderHotbarStrip(graphics, mouseX, mouseY);
        }

        // Only show a notice in the viewport if no world is loaded
        if (minecraft.level == null || minecraft.player == null) {
            int viewX = panelOnRight ? 16 : panelX + panelWidth + 16;
            int viewW = Math.max(80, width - panelWidth - 36);
            graphics.textWithWordWrap(font, Component.translatable("locomotion.config.held_models.editor.join_world"),
                    viewX, height / 2 - 12, viewW, 0xFFFFD080);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHotbarStrip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Inventory inv = minecraft.player.getInventory();
        int selectedSlot = inv.getSelectedSlot();
        Component hoveredTooltip = null;

        for (int i = 0; i < 9; i++) {
            int sx = hotbarStripX + i * 21;
            boolean active = hand == InteractionHand.MAIN_HAND && selectedSlot == i;
            boolean hovered = mouseX >= sx && mouseX < sx + 20 && mouseY >= hotbarStripY && mouseY < hotbarStripY + 20;
            ItemStack stack = inv.getItem(i);
            drawSlotCell(graphics, sx, hotbarStripY, stack, active, hovered, String.valueOf(i + 1));
            if (hovered) {
                hoveredTooltip = stack.isEmpty()
                        ? Component.translatable("locomotion.config.held_models.editor.main_hand").append(" (" + (i + 1) + ")")
                        : stack.getHoverName();
            }
        }

        int offX = hotbarStripX + 9 * 21 + 5;
        boolean offActive = hand == InteractionHand.OFF_HAND;
        boolean offHovered = mouseX >= offX && mouseX < offX + 20 && mouseY >= hotbarStripY && mouseY < hotbarStripY + 20;
        ItemStack offStack = minecraft.player.getItemInHand(InteractionHand.OFF_HAND);
        drawSlotCell(graphics, offX, hotbarStripY, offStack, offActive, offHovered, "F");
        if (offHovered) {
            hoveredTooltip = offStack.isEmpty()
                    ? Component.translatable("locomotion.config.held_models.editor.off_hand")
                    : offStack.getHoverName();
        }

        if (hoveredTooltip != null) {
            graphics.setTooltipForNextFrame(font, hoveredTooltip, mouseX, mouseY);
        }
    }

    private void drawSlotCell(GuiGraphicsExtractor graphics, int x, int y, ItemStack stack,
                              boolean active, boolean hovered, String badgeText) {
        int bg = active ? SLOT_ACTIVE_BG : (hovered ? SLOT_HOVER_BG : SLOT_BG);
        int border = active ? ACCENT_COLOR : BORDER_COLOR;
        graphics.fill(x, y, x + 20, y + 20, bg);
        drawOutline(graphics, x, y, 20, 20, border);
        if (!stack.isEmpty()) {
            graphics.fakeItem(stack, x + 2, y + 2);
        } else {
            graphics.centeredText(font, Component.literal(badgeText), x + 10, y + 6, TEXT_MUTED);
        }
    }

    static void drawOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override public boolean isInGameUi() { return true; }
    @Override public boolean isPauseScreen() { return true; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private final class ModelSlider extends AbstractSliderButton {
        private final String key;
        private final SliderKind kind;
        private final Function<ItemModelTransform, Float> getter;
        private final java.util.function.Consumer<Float> setter;

        ModelSlider(int x, int y, int width, String key, SliderKind kind,
                    Function<ItemModelTransform, Float> getter, java.util.function.Consumer<Float> setter) {
            super(x, y, width, 18, Component.empty(), (getter.apply(editor.current()) - kind.min) / kind.span);
            this.key = key;
            this.kind = kind;
            this.getter = getter;
            this.setter = setter;
            updateMessage();
        }

        void syncFromModel() {
            this.value = Math.clamp((getter.apply(editor.current()) - kind.min) / kind.span, 0.0, 1.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("locomotion.config.held_models." + key).append(": ")
                    .append(String.format(Locale.ROOT, "%.2f%s", getter.apply(editor.current()), kind.unit)));
        }

        @Override
        protected void applyValue() {
            double actual = kind.min + value * kind.span;
            setter.accept(kind.round(actual));
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (canChangeValue && (event.isLeft() || event.isRight())) {
                setValue(value + (event.isLeft() ? -1 : 1) * (kind.step / kind.span));
                return true;
            }
            return super.keyPressed(event);
        }
    }
}
