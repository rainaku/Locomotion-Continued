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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Uses the real in-world hand renderer as its preview, with no custom GPU pipeline. */
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

    private final Screen parent;
    private final LocomotionConfig config;
    private final HeldItemModelEditor editor;
    private final List<ModelSlider> sliders = new ArrayList<>();
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    private String selectedItem;
    private Tab tab = Tab.POSITION;
    private boolean panelOnRight;
    private int panelX;
    private int panelWidth;
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
                && !minecraft.player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) hand = InteractionHand.OFF_HAND;
    }

    @Override
    public void added() {
        config.beginHeldItemPreview(editor.preview());
        if (minecraft.level != null && !previewStarted) {
            previousCamera = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            hudWasHidden = minecraft.gui.hud.isHidden();
            if (!hudWasHidden) minecraft.gui.hud.toggle();
            previewStarted = true;
        }
    }

    @Override
    public void removed() {
        config.endHeldItemPreview(editor.preview());
        if (previewStarted) {
            minecraft.options.setCameraType(previousCamera);
            if (minecraft.gui.hud.isHidden() != hudWasHidden) minecraft.gui.hud.toggle();
            previewStarted = false;
        }
    }

    @Override
    protected void init() {
        sliders.clear();
        updateTarget();
        panelWidth = Math.min(250, Math.max(140, width / 2 - 12));
        panelX = panelOnRight ? width - panelWidth - 6 : 6;
        int x = panelX + 6;
        int w = panelWidth - 12;
        int half = (w - 4) / 2;
        int third = (w - 8) / 3;
        var global = button("mode.global", x, 24, half, () -> setMode(HeldItemModelSettings.Mode.GLOBAL));
        global.active = editor.preview().mode != HeldItemModelSettings.Mode.GLOBAL;
        var specific = button("mode.specific", x + half + 4, 24, w - half - 4, () -> setMode(HeldItemModelSettings.Mode.SPECIFIC));
        specific.active = selectedItem != null && editor.preview().mode != HeldItemModelSettings.Mode.SPECIFIC;
        boolean canCycleSlot = minecraft.player != null && hand == InteractionHand.MAIN_HAND;
        int handWidth = canCycleSlot ? w - 40 : w;
        addRenderableWidget(Button.builder(handName(), ignored -> {
            hand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            rebuildWidgets();
        }).bounds(x, 46, handWidth, 18).build());
        if (canCycleSlot) {
            addRenderableWidget(Button.builder(Component.literal("<"), ignored -> cycleHotbarSlot(-1))
                    .bounds(x + handWidth + 2, 46, 18, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), ignored -> cycleHotbarSlot(1))
                    .bounds(x + handWidth + 22, 46, 18, 18).build());
        }
        var position = button("editor.position", x, 82, third, () -> { tab = Tab.POSITION; rebuildWidgets(); });
        position.active = tab != Tab.POSITION;
        var rotation = button("editor.rotation", x + third + 4, 82, third, () -> { tab = Tab.ROTATION; rebuildWidgets(); });
        rotation.active = tab != Tab.ROTATION;
        var pivot = button("editor.pivot", x + (third + 4) * 2, 82, w - (third + 4) * 2, () -> { tab = Tab.PIVOT; rebuildWidgets(); });
        pivot.active = tab != Tab.PIVOT;
        SliderKind axisKind = tab == Tab.ROTATION ? SliderKind.ANGLE : SliderKind.PIXEL;
        String tooltipKey = tab == Tab.PIVOT ? "pivot.description" : axisKind.descriptionKey;
        addSlider(x, 104, w, axisKey("x"), axisKind, tooltipKey,
                t -> switch (tab) { case POSITION -> t.positionX; case ROTATION -> t.rotationX; case PIVOT -> t.pivotX; },
                (t, value) -> { switch (tab) { case POSITION -> t.positionX = value; case ROTATION -> t.rotationX = value; case PIVOT -> t.pivotX = value; } });
        addSlider(x, 126, w, axisKey("y"), axisKind, tooltipKey,
                t -> switch (tab) { case POSITION -> t.positionY; case ROTATION -> t.rotationY; case PIVOT -> t.pivotY; },
                (t, value) -> { switch (tab) { case POSITION -> t.positionY = value; case ROTATION -> t.rotationY = value; case PIVOT -> t.pivotY = value; } });
        addSlider(x, 148, w, axisKey("z"), axisKind, tooltipKey,
                t -> switch (tab) { case POSITION -> t.positionZ; case ROTATION -> t.rotationZ; case PIVOT -> t.pivotZ; },
                (t, value) -> { switch (tab) { case POSITION -> t.positionZ = value; case ROTATION -> t.rotationZ = value; case PIVOT -> t.pivotZ = value; } });
        addSlider(x, 174, w, "scale", SliderKind.SCALE, SliderKind.SCALE.descriptionKey, t -> t.scale, (t, value) -> t.scale = value);
        var reset = button(editor.preview().mode == HeldItemModelSettings.Mode.GLOBAL ? "editor.reset" : "editor.inherit",
                x, 196, w, () -> { editor.reset(); rebuildWidgets(); });
        reset.active = editor.canEdit();
        addRenderableWidget(Button.builder(Component.translatable("locomotion.config.held_models.editor.save"), ignored -> {
            config.data().firstPersonPlayer.heldItemModels = editor.snapshot();
            config.save();
            onClose();
        }).bounds(x, height - 22, half, 18).build());
        button("editor.cancel", x + half + 4, height - 22, w - half - 4, this::onClose);
    }

    private String axisKey(String axis) {
        return switch (tab) {
            case POSITION -> "position_" + axis;
            case ROTATION -> "rotation_" + axis;
            case PIVOT -> "pivot_" + axis;
        };
    }

    private void cycleHotbarSlot(int delta) {
        if (minecraft.player == null) return;
        Inventory inventory = minecraft.player.getInventory();
        inventory.setSelectedSlot(Math.floorMod(inventory.getSelectedSlot() + delta, 9));
        rebuildWidgets();
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.translatable("locomotion.config.held_models." + key), ignored -> action.run())
                .bounds(x, y, width, 18).build());
    }

    private void addSlider(int x, int y, int width, String key, SliderKind kind, String tooltipKey,
                           Function<ItemModelTransform, Float> getter, BiConsumer<ItemModelTransform, Float> setter) {
        var slider = new ModelSlider(x, y, width, key, kind, getter, value -> editor.edit(transform -> setter.accept(transform, value)));
        slider.active = editor.canEdit();
        slider.setTooltip(Tooltip.create(Component.translatable("locomotion.config.held_models." + tooltipKey)));
        sliders.add(slider);
        addRenderableWidget(slider);
    }

    private void syncSliders() {
        for (ModelSlider slider : sliders) {
            slider.syncFromModel();
        }
    }

    private void setMode(HeldItemModelSettings.Mode mode) {
        editor.setMode(mode);
        rebuildWidgets();
    }

    private String itemInHand() {
        if (minecraft.player == null || minecraft.player.getItemInHand(hand).isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(minecraft.player.getItemInHand(hand).getItem()).toString();
    }

    private boolean isLeftArm() {
        if (minecraft.player == null) return false;
        HumanoidArm side = hand == InteractionHand.MAIN_HAND ? minecraft.player.getMainArm() : minecraft.player.getMainArm().getOpposite();
        return side == HumanoidArm.LEFT;
    }

    private void updateTarget() {
        selectedItem = itemInHand();
        editor.selectItem(selectedItem);
        if (minecraft.player != null) {
            panelOnRight = isLeftArm();
        }
    }

    private boolean isInPanel(double mouseX) {
        return mouseX >= panelX && mouseX < panelX + panelWidth;
    }

    private Component handName() {
        return Component.translatable("locomotion.config.held_models.editor." + (hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand"));
    }

    @Override
    public void tick() {
        if (!Objects.equals(selectedItem, itemInHand())) rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!isInPanel(event.x()) && editor.canEdit() && (event.button() == 0 || event.button() == 1)) {
            draggingPreview = true;
            dragButton = event.button();
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
            syncSliders();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isInPanel(mouseX) && editor.canEdit() && scrollY != 0) {
            editor.edit(t -> t.scale = SliderKind.SCALE.round(t.scale + scrollY * 0.05));
            syncSliders();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.player != null && hand == InteractionHand.MAIN_HAND && event.key() >= 49 && event.key() <= 57) {
            minecraft.player.getInventory().setSelectedSlot(event.key() - 49);
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // Leave the actual world and held model clear; no full-screen tint or blur.
        graphics.fill(panelX, 0, panelX + panelWidth, height, 0xDB10151F);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, title, panelX + panelWidth / 2, 8, 0xFFFFFFFF);
        graphics.enableScissor(panelX + 6, 66, panelX + panelWidth - 6, 80);
        graphics.text(font, selectedItem == null ? Component.translatable("locomotion.config.held_models.editor.empty_hand") : Component.literal(selectedItem),
                panelX + 6, 68, 0xFFB8C7D9);
        graphics.disableScissor();
        int textX = panelOnRight ? 8 : panelX + panelWidth + 8;
        int textWidth = Math.max(40, width - panelWidth - 24);
        int helpY = graphics.textWithWordWrap(font, Component.translatable("locomotion.config.held_models.editor.live",
                Component.translatable("locomotion.config.held_models.mode." + editor.preview().mode.name().toLowerCase(Locale.ROOT))),
                textX, 12, textWidth, 0xFF8AFFAA) + 8;
        int hintY = graphics.textWithWordWrap(font, Component.translatable("locomotion.config.held_models.editor.help"),
                textX, helpY, textWidth, 0xFFFFFFFF) + 8;
        if (minecraft.level == null || minecraft.player == null) {
            graphics.textWithWordWrap(font, Component.translatable("locomotion.config.held_models.editor.join_world"), textX, hintY, textWidth, 0xFFFFD080);
        } else if (tab == Tab.PIVOT && isIdentityScaleAndRotation(editor.current())) {
            graphics.textWithWordWrap(font, Component.translatable("locomotion.config.held_models.editor.pivot_hint"), textX, hintY, textWidth, 0xFFFFD080);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static boolean isIdentityScaleAndRotation(ItemModelTransform t) {
        return t.scale == 1f && t.rotationX == 0f && t.rotationY == 0f && t.rotationZ == 0f;
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

        @Override protected void updateMessage() {
            setMessage(Component.translatable("locomotion.config.held_models." + key).append(": ")
                    .append(String.format(Locale.ROOT, "%.2f%s", getter.apply(editor.current()), kind.unit)));
        }

        @Override protected void applyValue() {
            double actual = kind.min + value * kind.span;
            setter.accept(kind.round(actual));
        }

        @Override public boolean keyPressed(KeyEvent event) {
            if (canChangeValue && (event.isLeft() || event.isRight())) {
                setValue(value + (event.isLeft() ? -1 : 1) * (kind.step / kind.span));
                return true;
            }
            return super.keyPressed(event);
        }
    }
}
