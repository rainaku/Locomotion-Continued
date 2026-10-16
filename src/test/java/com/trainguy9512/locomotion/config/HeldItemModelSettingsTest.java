package com.trainguy9512.locomotion.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeldItemModelSettingsTest {
    @Test
    void globalModeIgnoresOverridesAndSpecificModeFallsBackToGlobal() {
        var settings = new HeldItemModelSettings();
        var sword = new ItemModelTransform();
        sword.scale = 0.5f;
        settings.specific.put("minecraft:diamond_sword", sword);
        assertSame(settings.global, settings.resolve("minecraft:diamond_sword"));
        settings.mode = HeldItemModelSettings.Mode.SPECIFIC;
        assertSame(sword, settings.resolve("minecraft:diamond_sword"));
        assertSame(settings.global, settings.resolve("minecraft:filled_map"));
        settings.specific.put("example:custom/item", sword);
        assertSame(sword, settings.resolve("example:custom/item"));
    }

    @Test
    void newSpecificProfileCanBeEditedWithoutChangingGlobal() {
        var settings = new HeldItemModelSettings();
        settings.global.positionX = 4;
        var draft = settings.global.copy();
        draft.positionX = 10;
        assertEquals(4, settings.global.positionX);
        assertTrue(settings.specific.isEmpty());
    }

    @Test
    void existingConfigurationKeepsIdentityDefaults() throws Exception {
        var data = gson().fromJson("{\"first_person_player\":{\"enable_renderer\":true}}", LocomotionConfig.Data.class);
        assertEquals(HeldItemModelSettings.Mode.GLOBAL, data.firstPersonPlayer.heldItemModels.mode);
        assertEquals(1f, data.firstPersonPlayer.heldItemModels.global.scale);
        assertEquals(0f, data.firstPersonPlayer.heldItemModels.global.positionX);
        assertTrue(data.firstPersonPlayer.heldItemModels.specific.isEmpty());
    }

    @Test
    void profilesRoundTripThroughTheActualConfigSerializer() throws Exception {
        var data = new LocomotionConfig.Data();
        var settings = data.firstPersonPlayer.heldItemModels;
        settings.mode = HeldItemModelSettings.Mode.SPECIFIC;
        settings.global.positionZ = -3;
        var profile = new ItemModelTransform();
        profile.scale = 1.75f;
        profile.pivotY = 5;
        settings.specific.put("minecraft:diamond_sword", profile);
        String json = gson().toJson(data);
        assertTrue(json.contains("held_item_models"));
        assertTrue(json.contains("pivot_y"));
        var loaded = gson().fromJson(json, LocomotionConfig.Data.class).firstPersonPlayer.heldItemModels;
        loaded.sanitize();
        assertEquals(1.75f, loaded.resolve("minecraft:diamond_sword").scale);
        assertEquals(5f, loaded.resolve("minecraft:diamond_sword").pivotY);
        assertEquals(-3f, loaded.resolve("minecraft:torch").positionZ);
    }

    @Test
    void repairsNullsUnknownModesAndMalformedProfiles() throws Exception {
        var settings = gson().fromJson("""
                {"mode":"UNKNOWN","global":null,"specific":{
                  "bad item id":{},"minecraft:stick":null,"example:item":{"scale":0}}}
                """, HeldItemModelSettings.class);
        settings.sanitize();
        assertEquals(HeldItemModelSettings.Mode.GLOBAL, settings.mode);
        assertEquals(1f, settings.global.scale);
        assertEquals(1, settings.specific.size());
        assertEquals(0.05f, settings.specific.get("example:item").scale);
        settings.specific = null;
        settings.sanitize();
        assertTrue(settings.specific.isEmpty());
    }

    private static Gson gson() throws Exception {
        var field = LocomotionConfig.class.getDeclaredField("GSON");
        field.setAccessible(true);
        return (Gson) field.get(null);
    }
}
