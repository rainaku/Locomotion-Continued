package com.trainguy9512.locomotion.config;

import com.trainguy9512.locomotion.LocomotionMain;
import com.trainguy9512.locomotion.animation.animator.JointAnimator;
import com.trainguy9512.locomotion.animation.animator.JointAnimatorRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Custom configuration screen with tabbed categories, scrollable rows, and per-option reset. */
public class LocomotionConfigScreen extends Screen {
    private enum Category {
        FIRST_PERSON,
        BLOCK_ENTITIES
    }

    private sealed interface RowItem permits SectionHeader, SettingRow {}

    private record SectionHeader(Component title, Component description) implements RowItem {}

    private record SettingRow(
            Component label,
            Component description,
            AbstractWidget control,
            Button resetButton,
            Supplier<Boolean> isModified,
            Runnable resetAction,
            Runnable syncControl
    ) implements RowItem {
        void refreshState() {
            syncControl.run();
            resetButton.active = isModified.get();
        }
    }

    private static final int BG_DARK = 0xE60D1219;
    private static final int BAR_BG = 0xF2141C27;
    private static final int ROW_EVEN_BG = 0x6616202E;
    private static final int ROW_HOVER_BG = 0x991E2C40;
    private static final int BORDER_COLOR = 0xFF283548;
    private static final int ACCENT_COLOR = 0xFF4CA6FF;
    private static final int TEXT_PRIMARY = 0xFFF1F5F9;
    private static final int TEXT_SECTION = 0xFF7CC4FA;
    private static final int TEXT_ON = 0xFF4ADE80;
    private static final int TEXT_OFF = 0xFF94A3B8;

    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 32;
    private static final int ROW_HEIGHT = 24;
    private static final int SECTION_HEIGHT = 22;

    private final Screen parentScreen;
    private final LocomotionConfig config;
    private final List<RowItem> rows = new ArrayList<>();
    private Category activeCategory = Category.FIRST_PERSON;
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private int listLeft;
    private int listWidth;
    private int listTop;
    private int listBottom;
    private AbstractWidget draggingRowWidget;

    public LocomotionConfigScreen(Screen parentScreen, LocomotionConfig config) {
        super(Component.translatable("locomotion.config.title"));
        this.parentScreen = parentScreen;
        this.config = config;
    }

    public static Screen createConfigScreen(Screen parentScreen) {
        return new LocomotionConfigScreen(parentScreen, LocomotionMain.CONFIG);
    }

    @Override
    protected void init() {
        rows.clear();
        draggingRowWidget = null;
        listWidth = Math.min(440, width - 24);
        listLeft = (width - listWidth) / 2;
        listTop = HEADER_HEIGHT + 6;
        listBottom = height - FOOTER_HEIGHT - 6;

        // Top Navigation Tabs: [First Person] [Held Item Models (Live Preview)] [Block Entities]
        int tabWidth = Math.min(138, (listWidth - 8) / 3);
        int tabStartX = (width - (tabWidth * 3 + 8)) / 2;
        int tabY = 8;

        Button fpTab = addRenderableWidget(Button.builder(
                Component.translatable("locomotion.config.category.first_person_player.name"),
                ignored -> switchCategory(Category.FIRST_PERSON)
        ).bounds(tabStartX, tabY, tabWidth, 20).build());
        fpTab.active = activeCategory != Category.FIRST_PERSON;

        addRenderableWidget(Button.builder(
                Component.translatable("locomotion.config.held_models.title"),
                ignored -> minecraft.gui.setScreen(new HeldItemModelScreen(this, config))
        ).bounds(tabStartX + tabWidth + 4, tabY, tabWidth, 20).build());

        Button beTab = addRenderableWidget(Button.builder(
                Component.translatable("locomotion.config.category.block_entities.name"),
                ignored -> switchCategory(Category.BLOCK_ENTITIES)
        ).bounds(tabStartX + (tabWidth + 4) * 2, tabY, tabWidth, 20).build());
        beTab.active = activeCategory != Category.BLOCK_ENTITIES;

        // Build rows for the active category
        if (activeCategory == Category.FIRST_PERSON) {
            buildFirstPersonRows();
        } else {
            buildBlockEntityRows();
        }

        // Bottom Footer: [Reset Category] [Save & Close] [Cancel]
        int footerY = height - FOOTER_HEIGHT + 6;
        int btnW = Math.min(120, (listWidth - 8) / 3);
        int footerStartX = (width - (btnW * 3 + 8)) / 2;

        addRenderableWidget(Button.builder(
                Component.translatable("locomotion.config.held_models.editor.reset"),
                ignored -> resetCurrentCategory()
        ).bounds(footerStartX, footerY, btnW, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("locomotion.config.held_models.editor.save"),
                ignored -> {
                    config.save();
                    onClose();
                }
        ).bounds(footerStartX + btnW + 4, footerY, btnW, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("locomotion.config.held_models.editor.cancel"),
                ignored -> {
                    config.load();
                    onClose();
                }
        ).bounds(footerStartX + (btnW + 4) * 2, footerY, btnW, 20).build());

        updateLayout();
    }

    private void switchCategory(Category category) {
        if (this.activeCategory != category) {
            this.activeCategory = category;
            this.scrollOffset = 0;
            rebuildWidgets();
        }
    }

    private void resetCurrentCategory() {
        for (RowItem item : rows) {
            if (item instanceof SettingRow row) {
                row.resetAction().run();
                row.refreshState();
            }
        }
    }

    private void buildFirstPersonRows() {
        var fp = config.data().firstPersonPlayer;

        addBooleanRow(
                "locomotion.config.option.enable_first_person_renderer.name",
                "locomotion.config.option.enable_first_person_renderer.description",
                true,
                () -> fp.enableRenderer,
                v -> fp.enableRenderer = v
        );
        addFloatRow(
                "locomotion.config.option.first_person_mining_speed.name",
                "locomotion.config.option.first_person_mining_speed.description",
                1.0f, 0.25f, 2.00f, 0.01f, "%.2fx",
                () -> fp.miningAnimationSpeedMultiplier,
                v -> fp.miningAnimationSpeedMultiplier = v
        );
        addFloatRow(
                "locomotion.config.option.running_arm_swing_intensity.name",
                "locomotion.config.option.running_arm_swing_intensity.description",
                1.0f, 0.00f, 1.00f, 0.01f, "%.2f",
                () -> fp.runningArmSwingIntensity,
                v -> fp.runningArmSwingIntensity = v
        );

        // Quick action row to open the Live 3D Held Item Editor
        addActionRow(
                "locomotion.config.held_models.title",
                "locomotion.config.held_models.description",
                "locomotion.config.held_models.editor.open",
                () -> minecraft.gui.setScreen(new HeldItemModelScreen(this, config))
        );

        addSection("locomotion.config.group.first_person_camera_shake.name",
                "locomotion.config.group.first_person_camera_shake.description");
        addFloatRow(
                "locomotion.config.option.first_person_camera_shake_intensity_master.name",
                "locomotion.config.option.first_person_camera_shake_intensity_master.description",
                1.0f, 0.00f, 1.00f, 0.01f, "%.2f",
                () -> fp.cameraShakeMasterIntensity,
                v -> fp.cameraShakeMasterIntensity = v
        );
        addFloatRow(
                "locomotion.config.option.first_person_camera_shake_intensity_movement.name",
                "locomotion.config.option.first_person_camera_shake_intensity_movement.description",
                1.0f, 0.00f, 1.00f, 0.01f, "%.2f",
                () -> fp.cameraShakeMovementIntensity,
                v -> fp.cameraShakeMovementIntensity = v
        );
        addFloatRow(
                "locomotion.config.option.first_person_camera_shake_intensity_item_interactions.name",
                "locomotion.config.option.first_person_camera_shake_intensity_item_interactions.description",
                0.5f, 0.00f, 1.00f, 0.01f, "%.2f",
                () -> fp.cameraShakeItemInteractionIntensity,
                v -> fp.cameraShakeItemInteractionIntensity = v
        );

        addSection("locomotion.config.group.first_person_arm_camera_damping.name",
                "locomotion.config.group.first_person_arm_camera_damping.description");
        addBooleanRow(
                "locomotion.config.option.enable_first_person_arm_camera_damping.name",
                "locomotion.config.option.enable_first_person_arm_camera_damping.description",
                true,
                () -> fp.enableCameraRotationDamping,
                v -> fp.enableCameraRotationDamping = v
        );
        addFloatRow(
                "locomotion.config.option.first_person_arm_camera_stiffness_factor.name",
                "locomotion.config.option.first_person_arm_camera_stiffness_factor.description",
                0.3f, 0.01f, 1.00f, 0.01f, "%.2f",
                () -> fp.cameraRotationStiffnessFactor,
                v -> fp.cameraRotationStiffnessFactor = v
        );
        addFloatRow(
                "locomotion.config.option.first_person_arm_camera_damping_factor.name",
                "locomotion.config.option.first_person_arm_camera_damping_factor.description",
                0.65f, 0.01f, 1.00f, 0.01f, "%.2f",
                () -> fp.cameraRotationDampingFactor,
                v -> fp.cameraRotationDampingFactor = v
        );

        addSection("locomotion.config.group.first_person_map.name",
                "locomotion.config.group.first_person_map.description");
        addFloatRow(
                "locomotion.config.option.first_person_map_movement_animation_intensity.name",
                "locomotion.config.option.first_person_map_movement_animation_intensity.description",
                0.2f, 0.00f, 1.00f, 0.01f, "%.2f",
                () -> fp.mapMovementAnimationIntensity,
                v -> fp.mapMovementAnimationIntensity = v
        );

        addSection("locomotion.config.group.first_person_arm_offset.name",
                "locomotion.config.group.first_person_arm_offset.description");
        addFloatRow(
                "locomotion.config.option.first_person_arm_offset_x.name",
                "locomotion.config.option.first_person_arm_offset_x.description",
                0.0f, -16.0f, 16.0f, 0.1f, "%.1f px",
                () -> fp.armOffsetX,
                v -> fp.armOffsetX = v
        );
        addFloatRow(
                "locomotion.config.option.first_person_arm_offset_y.name",
                "locomotion.config.option.first_person_arm_offset_y.description",
                0.0f, -16.0f, 16.0f, 0.1f, "%.1f px",
                () -> fp.armOffsetY,
                v -> fp.armOffsetY = v
        );
        addFloatRow(
                "locomotion.config.option.first_person_arm_offset_z.name",
                "locomotion.config.option.first_person_arm_offset_z.description",
                0.0f, -16.0f, 16.0f, 0.1f, "%.1f px",
                () -> fp.armOffsetZ,
                v -> fp.armOffsetZ = v
        );
    }

    private void buildBlockEntityRows() {
        var be = config.data().blockEntities;

        addSection("locomotion.config.group.block_entity_optimization.name",
                "locomotion.config.group.block_entity_optimization.description");
        addIntRow(
                "locomotion.config.option.block_entity_animation_distance.name",
                "locomotion.config.option.block_entity_animation_distance.description",
                32, 4, 128, 4,
                () -> be.evaluationDistance,
                v -> be.evaluationDistance = v
        );
        addEnumRow(
                "locomotion.config.option.block_entity_pose_calculation_frequency.name",
                "locomotion.config.option.block_entity_pose_calculation_frequency.description",
                JointAnimator.PoseCalculationFrequency.CALCULATE_EVERY_FRAME,
                JointAnimator.PoseCalculationFrequency.values(),
                () -> be.poseCalculationFrequency,
                v -> be.poseCalculationFrequency = v,
                v -> Component.translatable("locomotion.config.option.block_entity_pose_calculation_frequency.enum." + v.name().toLowerCase(Locale.ROOT))
        );

        addSection("locomotion.config.group.individual_block_entity_toggles.name",
                "locomotion.config.group.individual_block_entity_toggles.description");
        for (BlockEntityType<?> blockEntityType : JointAnimatorRegistry.getRegisteredBlockEntities()) {
            Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntityType);
            if (id == null) continue;
            String key = id.toString();
            addBooleanRowComponent(
                    Component.translatable("block." + id.toLanguageKey()),
                    null,
                    true,
                    () -> be.enabledBlockEntities.getOrDefault(key, true),
                    v -> be.enabledBlockEntities.put(key, v)
            );
        }
    }

    private void addSection(String titleKey, String descKey) {
        rows.add(new SectionHeader(Component.translatable(titleKey), Component.translatable(descKey)));
    }

    private void addActionRow(String labelKey, String descKey, String buttonKey, Runnable action) {
        Button btn = Button.builder(Component.translatable(buttonKey), ignored -> action.run())
                .bounds(0, 0, 166, 18).build();
        Button dummyReset = Button.builder(Component.literal("R"), ignored -> {}).bounds(0, 0, 18, 18).build();
        dummyReset.visible = false;
        dummyReset.active = false;
        rows.add(new SettingRow(
                Component.translatable(labelKey),
                Component.translatable(descKey),
                btn,
                dummyReset,
                () -> false,
                () -> {},
                () -> {}
        ));
    }

    private void addBooleanRow(String labelKey, String descKey, boolean defaultVal,
                               Supplier<Boolean> getter, Consumer<Boolean> setter) {
        addBooleanRowComponent(Component.translatable(labelKey), Component.translatable(descKey), defaultVal, getter, setter);
    }

    private void addBooleanRowComponent(Component label, Component desc, boolean defaultVal,
                                        Supplier<Boolean> getter, Consumer<Boolean> setter) {
        final SettingRow[] holder = new SettingRow[1];
        Button toggle = Button.builder(Component.empty(), ignored -> {
            setter.accept(!getter.get());
            if (holder[0] != null) holder[0].refreshState();
        }).bounds(0, 0, 144, 18).build();

        Button resetBtn = Button.builder(Component.literal("R"), ignored -> {
            setter.accept(defaultVal);
            if (holder[0] != null) holder[0].refreshState();
        }).bounds(0, 0, 18, 18).build();

        Runnable sync = () -> {
            boolean state = getter.get();
            toggle.setMessage(Component.translatable(state
                    ? "locomotion.config.option.enable_first_person_renderer.enabled"
                    : "locomotion.config.option.enable_first_person_renderer.disabled")
                    .withStyle(style -> style.withColor(state ? TEXT_ON : TEXT_OFF)));
        };

        SettingRow row = new SettingRow(label, desc, toggle, resetBtn, () -> getter.get() != defaultVal, () -> setter.accept(defaultVal), sync);
        holder[0] = row;
        row.refreshState();
        rows.add(row);
    }

    private void addFloatRow(String labelKey, String descKey, float defaultVal, float min, float max, float step,
                             String format, Supplier<Float> getter, Consumer<Float> setter) {
        final SettingRow[] holder = new SettingRow[1];
        ConfigFloatSlider slider = new ConfigFloatSlider(0, 0, 144, min, max, step, format, getter, value -> {
            setter.accept(value);
            if (holder[0] != null) holder[0].resetButton().active = Math.abs(getter.get() - defaultVal) > 1e-4f;
        });

        Button resetBtn = Button.builder(Component.literal("R"), ignored -> {
            setter.accept(defaultVal);
            if (holder[0] != null) holder[0].refreshState();
        }).bounds(0, 0, 18, 18).build();

        SettingRow row = new SettingRow(
                Component.translatable(labelKey),
                Component.translatable(descKey),
                slider,
                resetBtn,
                () -> Math.abs(getter.get() - defaultVal) > 1e-4f,
                () -> setter.accept(defaultVal),
                slider::syncFromModel
        );
        holder[0] = row;
        row.refreshState();
        rows.add(row);
    }

    private void addIntRow(String labelKey, String descKey, int defaultVal, int min, int max, int step,
                           Supplier<Integer> getter, Consumer<Integer> setter) {
        final SettingRow[] holder = new SettingRow[1];
        ConfigIntSlider slider = new ConfigIntSlider(0, 0, 144, min, max, step, getter, value -> {
            setter.accept(value);
            if (holder[0] != null) holder[0].resetButton().active = !Objects.equals(getter.get(), defaultVal);
        });

        Button resetBtn = Button.builder(Component.literal("R"), ignored -> {
            setter.accept(defaultVal);
            if (holder[0] != null) holder[0].refreshState();
        }).bounds(0, 0, 18, 18).build();

        SettingRow row = new SettingRow(
                Component.translatable(labelKey),
                Component.translatable(descKey),
                slider,
                resetBtn,
                () -> !Objects.equals(getter.get(), defaultVal),
                () -> setter.accept(defaultVal),
                slider::syncFromModel
        );
        holder[0] = row;
        row.refreshState();
        rows.add(row);
    }

    private <E extends Enum<E>> void addEnumRow(String labelKey, String descKey, E defaultVal, E[] values,
                                                Supplier<E> getter, Consumer<E> setter,
                                                 java.util.function.Function<E, Component> formatter) {
        final SettingRow[] holder = new SettingRow[1];
        Button cycleBtn = Button.builder(Component.empty(), ignored -> {
            E current = getter.get();
            int nextIndex = (current.ordinal() + 1) % values.length;
            setter.accept(values[nextIndex]);
            if (holder[0] != null) holder[0].refreshState();
        }).bounds(0, 0, 144, 18).build();

        Button resetBtn = Button.builder(Component.literal("R"), ignored -> {
            setter.accept(defaultVal);
            if (holder[0] != null) holder[0].refreshState();
        }).bounds(0, 0, 18, 18).build();

        SettingRow row = new SettingRow(
                Component.translatable(labelKey),
                Component.translatable(descKey),
                cycleBtn,
                resetBtn,
                () -> getter.get() != defaultVal,
                () -> setter.accept(defaultVal),
                () -> cycleBtn.setMessage(formatter.apply(getter.get()))
        );
        holder[0] = row;
        row.refreshState();
        rows.add(row);
    }

    private void updateLayout() {
        int contentHeight = 0;
        for (RowItem item : rows) {
            contentHeight += (item instanceof SectionHeader) ? SECTION_HEIGHT : ROW_HEIGHT;
        }
        int viewportHeight = Math.max(1, listBottom - listTop);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);

        int currentY = listTop - (int) Math.round(scrollOffset);
        int rightEdge = listLeft + listWidth - 6;

        for (RowItem item : rows) {
            if (item instanceof SectionHeader) {
                currentY += SECTION_HEIGHT;
            } else if (item instanceof SettingRow row) {
                int controlY = currentY + (ROW_HEIGHT - 18) / 2;
                if (row.resetButton().visible) {
                    row.resetButton().setPosition(rightEdge - 18, controlY);
                    row.control().setPosition(rightEdge - 18 - 4 - row.control().getWidth(), controlY);
                } else {
                    row.control().setPosition(rightEdge - row.control().getWidth(), controlY);
                }
                currentY += ROW_HEIGHT;
            }
        }
    }

    private boolean isInListViewport(double mouseY) {
        return mouseY >= listTop && mouseY < listBottom;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (isInListViewport(event.y())) {
            for (RowItem item : rows) {
                if (item instanceof SettingRow row) {
                    if (row.resetButton().visible && row.resetButton().mouseClicked(event, doubleClick)) {
                        return true;
                    }
                    if (row.control().mouseClicked(event, doubleClick)) {
                        draggingRowWidget = row.control();
                        setFocused(row.control());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingRowWidget != null && draggingRowWidget.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingRowWidget != null) {
            draggingRowWidget.mouseReleased(event);
            draggingRowWidget = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0 && scrollY != 0) {
            scrollOffset = Math.clamp(scrollOffset - scrollY * 18.0, 0, maxScroll);
            updateLayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, BG_DARK);
        // Header & Footer solid bars
        graphics.fill(0, 0, width, HEADER_HEIGHT, BAR_BG);
        graphics.fill(0, HEADER_HEIGHT, width, HEADER_HEIGHT + 1, BORDER_COLOR);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, BAR_BG);
        graphics.fill(0, height - FOOTER_HEIGHT - 1, width, height - FOOTER_HEIGHT, BORDER_COLOR);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Component hoveredDescription = null;

        graphics.enableScissor(0, listTop, width, listBottom);
        int currentY = listTop - (int) Math.round(scrollOffset);
        int rowIndex = 0;

        for (RowItem item : rows) {
            if (item instanceof SectionHeader section) {
                if (currentY + SECTION_HEIGHT >= listTop && currentY <= listBottom) {
                    graphics.text(font, section.title(), listLeft + 4, currentY + 7, TEXT_SECTION);
                    graphics.fill(listLeft + 4, currentY + SECTION_HEIGHT - 2, listLeft + listWidth - 4, currentY + SECTION_HEIGHT - 1, BORDER_COLOR);
                    if (isInListViewport(mouseY) && mouseX >= listLeft && mouseX < listLeft + listWidth
                            && mouseY >= currentY && mouseY < currentY + SECTION_HEIGHT) {
                        hoveredDescription = section.description();
                    }
                }
                currentY += SECTION_HEIGHT;
            } else if (item instanceof SettingRow row) {
                if (currentY + ROW_HEIGHT >= listTop && currentY <= listBottom) {
                    boolean rowHovered = isInListViewport(mouseY) && mouseX >= listLeft && mouseX < listLeft + listWidth
                            && mouseY >= currentY && mouseY < currentY + ROW_HEIGHT;
                    if (rowHovered) {
                        graphics.fill(listLeft, currentY, listLeft + listWidth, currentY + ROW_HEIGHT, ROW_HOVER_BG);
                        if (row.description() != null && mouseX < row.control().getX() - 4) {
                            hoveredDescription = row.description();
                        }
                    } else if (rowIndex % 2 == 0) {
                        graphics.fill(listLeft, currentY, listLeft + listWidth, currentY + ROW_HEIGHT, ROW_EVEN_BG);
                    }

                    int labelMaxX = row.control().getX() - 8;
                    graphics.enableScissor(listLeft + 8, Math.max(listTop, currentY), labelMaxX, Math.min(listBottom, currentY + ROW_HEIGHT));
                    graphics.text(font, row.label(), listLeft + 8, currentY + (ROW_HEIGHT - 8) / 2, TEXT_PRIMARY);
                    graphics.disableScissor();

                    row.control().extractRenderState(graphics, mouseX, mouseY, partialTick);
                    if (row.resetButton().visible) {
                        row.resetButton().extractRenderState(graphics, mouseX, mouseY, partialTick);
                    }
                }
                currentY += ROW_HEIGHT;
                rowIndex++;
            }
        }
        graphics.disableScissor();

        // Scrollbar indicator
        if (maxScroll > 0) {
            int trackX = listLeft + listWidth + 3;
            int trackH = listBottom - listTop;
            int thumbH = Math.max(20, (int) ((float) trackH * trackH / (trackH + maxScroll)));
            int thumbY = listTop + (int) ((scrollOffset / maxScroll) * (trackH - thumbH));
            graphics.fill(trackX, listTop, trackX + 3, listBottom, 0x661E293B);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, ACCENT_COLOR);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (hoveredDescription != null) {
            graphics.setTooltipForNextFrame(font, hoveredDescription, mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parentScreen);
    }

    private static final class ConfigFloatSlider extends AbstractSliderButton {
        private final float min;
        private final float span;
        private final float step;
        private final String format;
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;

        ConfigFloatSlider(int x, int y, int width, float min, float max, float step, String format,
                          Supplier<Float> getter, Consumer<Float> setter) {
            super(x, y, width, 18, Component.empty(), Math.clamp((getter.get() - min) / (max - min), 0.0, 1.0));
            this.min = min;
            this.span = max - min;
            this.step = step;
            this.format = format;
            this.getter = getter;
            this.setter = setter;
            updateMessage();
        }

        void syncFromModel() {
            this.value = Math.clamp((getter.get() - min) / span, 0.0, 1.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format(Locale.ROOT, format, getter.get())));
        }

        @Override
        protected void applyValue() {
            double raw = min + value * span;
            float snapped = Math.round(raw / step) * step;
            float clamped = Math.clamp(snapped, min, min + span);
            setter.accept(clamped);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (canChangeValue && (event.isLeft() || event.isRight())) {
                setValue(value + (event.isLeft() ? -1 : 1) * (step / span));
                return true;
            }
            return super.keyPressed(event);
        }
    }

    private static final class ConfigIntSlider extends AbstractSliderButton {
        private final int min;
        private final int span;
        private final int step;
        private final Supplier<Integer> getter;
        private final Consumer<Integer> setter;

        ConfigIntSlider(int x, int y, int width, int min, int max, int step,
                        Supplier<Integer> getter, Consumer<Integer> setter) {
            super(x, y, width, 18, Component.empty(), Math.clamp((double) (getter.get() - min) / (max - min), 0.0, 1.0));
            this.min = min;
            this.span = max - min;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            updateMessage();
        }

        void syncFromModel() {
            this.value = Math.clamp((double) (getter.get() - min) / span, 0.0, 1.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.valueOf(getter.get())));
        }

        @Override
        protected void applyValue() {
            double raw = min + value * span;
            int snapped = (int) Math.round(raw / step) * step;
            int clamped = Math.clamp(snapped, min, min + span);
            setter.accept(clamped);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (canChangeValue && (event.isLeft() || event.isRight())) {
                setValue(value + (event.isLeft() ? -1.0 : 1.0) * ((double) step / span));
                return true;
            }
            return super.keyPressed(event);
        }
    }
}
