package com.trainguy9512.locomotion.config;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeldItemModelEditorTest {
    @Test
    void sliderDraftImmediatelyChangesTheRenderTransformAndCancelRestoresSavedSettings() {
        var config = new LocomotionConfig();
        config.data().firstPersonPlayer.heldItemModels.global.positionX = 0.2f;
        var editor = new HeldItemModelEditor(config.data().firstPersonPlayer.heldItemModels);
        config.beginHeldItemPreview(editor.preview());
        editor.edit(t -> t.positionX = 16);
        var transformed = config.heldItemModelsForRendering().resolve("minecraft:stick").applyTo(new Matrix4f(), false);
        assertEquals(1f, transformed.transformPosition(new Vector3f()).x);
        assertEquals(0.2f, config.data().firstPersonPlayer.heldItemModels.global.positionX);
        config.endHeldItemPreview(editor.preview());
        assertEquals(0.2f, config.heldItemModelsForRendering().resolve("minecraft:stick").positionX);
    }

    @Test
    void switchingToSpecificPreviewsTheExistingSwordProfileFromTheReportedConfiguration() {
        var config = new LocomotionConfig();
        var saved = config.data().firstPersonPlayer.heldItemModels;
        var sword = new ItemModelTransform();
        sword.scale = 1.5f;
        saved.specific.put("minecraft:netherite_sword", sword);
        var editor = new HeldItemModelEditor(saved);
        editor.selectItem("minecraft:netherite_sword");
        config.beginHeldItemPreview(editor.preview());
        assertEquals(1f, config.heldItemModelsForRendering().resolve("minecraft:netherite_sword").scale);
        editor.setMode(HeldItemModelSettings.Mode.SPECIFIC);
        assertEquals(1.5f, config.heldItemModelsForRendering().resolve("minecraft:netherite_sword").scale);
        assertEquals(HeldItemModelSettings.Mode.GLOBAL, saved.mode);
    }

    @Test
    void specificSlidersCreateTheActiveOverrideWithoutAnotherEnableCheckbox() {
        var saved = new HeldItemModelSettings();
        var editor = new HeldItemModelEditor(saved);
        editor.selectItem("minecraft:diamond_sword");
        editor.setMode(HeldItemModelSettings.Mode.SPECIFIC);
        editor.edit(t -> t.scale = 2f);
        assertEquals(2f, editor.preview().resolve("minecraft:diamond_sword").scale);
        assertEquals(1f, editor.preview().resolve("minecraft:torch").scale);
        assertTrue(saved.specific.isEmpty());
    }

    @Test
    void savingKeepsTheModeAndProfileAfterThePreviewEnds() {
        var config = new LocomotionConfig();
        var editor = new HeldItemModelEditor(config.data().firstPersonPlayer.heldItemModels);
        config.beginHeldItemPreview(editor.preview());
        editor.selectItem("minecraft:diamond_sword");
        editor.setMode(HeldItemModelSettings.Mode.SPECIFIC);
        editor.edit(t -> t.scale = 2f);
        config.data().firstPersonPlayer.heldItemModels = editor.snapshot();
        config.endHeldItemPreview(editor.preview());
        editor.edit(t -> t.scale = 3f);
        assertEquals(2f, config.heldItemModelsForRendering().resolve("minecraft:diamond_sword").scale);
        assertEquals(HeldItemModelSettings.Mode.SPECIFIC, config.heldItemModelsForRendering().mode);
    }

    @Test
    void specificResetReturnsToGlobalWithoutChangingOtherSavedProfiles() {
        var saved = new HeldItemModelSettings();
        saved.global.scale = 0.75f;
        var editor = new HeldItemModelEditor(saved);
        editor.setMode(HeldItemModelSettings.Mode.SPECIFIC);
        editor.selectItem("minecraft:diamond_sword");
        editor.edit(t -> t.scale = 2f);
        editor.selectItem("minecraft:torch");
        editor.edit(t -> t.scale = 3f);
        editor.selectItem("minecraft:diamond_sword");
        editor.reset();
        assertEquals(0.75f, editor.current().scale);
        assertEquals(3f, editor.preview().resolve("minecraft:torch").scale);
        assertEquals(0.75f, saved.global.scale);
        assertTrue(saved.specific.isEmpty());
    }

    @Test
    void emptyHandsCannotCreateAnInvalidSpecificProfile() {
        var editor = new HeldItemModelEditor(new HeldItemModelSettings());
        editor.setMode(HeldItemModelSettings.Mode.SPECIFIC);
        editor.selectItem(null);
        assertFalse(editor.canEdit());
        editor.edit(t -> t.scale = 2f);
        assertTrue(editor.snapshot().specific.isEmpty());
        assertEquals(1f, editor.current().scale);
    }

    @Test
    void anOldScreenCannotClearAnotherScreensPreview() {
        var config = new LocomotionConfig();
        var first = new HeldItemModelEditor(config.data().firstPersonPlayer.heldItemModels);
        var second = new HeldItemModelEditor(config.data().firstPersonPlayer.heldItemModels);
        config.beginHeldItemPreview(first.preview());
        config.beginHeldItemPreview(second.preview());
        config.endHeldItemPreview(first.preview());
        assertSame(second.preview(), config.heldItemModelsForRendering());
    }
}
