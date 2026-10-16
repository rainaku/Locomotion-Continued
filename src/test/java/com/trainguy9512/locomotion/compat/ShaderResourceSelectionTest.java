package com.trainguy9512.locomotion.compat;

import com.trainguy9512.locomotion.mixin.render.MixinShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShaderResourceSelectionTest {
    private static final PackResources PACK = (PackResources) Proxy.newProxyInstance(
            PackResources.class.getClassLoader(), new Class<?>[]{PackResources.class},
            (proxy, method, args) -> {
                if (method.getName().equals("packId")) return "file/refined torches 2.1.zip";
                throw new UnsupportedOperationException(method.getName());
            });

    @Test
    void repairsSelectedPairAndPreservesPackIdentityAndOtherResources() throws Exception {
        var selected = legacyEntityResources();
        var vertexId = id("entity.vsh");
        var original = selected.get(vertexId);
        var customId = Identifier.fromNamespaceAndPath("other", "shaders/core/entity.vsh");
        selected.put(customId, resource("custom shader"));
        var item = LegacyRefinedShaders.repair("item", legacy("item.vsh"), legacy("item.fsh")).orElseThrow();
        selected.put(id("item.vsh"), resource(item.vertex()));
        selected.put(id("item.fsh"), resource(item.fragment()));

        var repaired = applyAdapter(selected);

        assertNotSame(selected, repaired);
        assertEquals(selected.keySet(), repaired.keySet());
        assertTrue(original.readAllAsString().contains("#moj_import"));
        assertFalse(repaired.get(vertexId).readAllAsString().contains("#moj_import"));
        assertSame(original.source(), repaired.get(vertexId).source());
        assertSame(original.metadata(), repaired.get(vertexId).metadata());
        assertEquals(original.sourcePackId(), repaired.get(vertexId).sourcePackId());
        assertSame(selected.get(customId), repaired.get(customId));
        assertSame(selected.get(id("item.vsh")), repaired.get(id("item.vsh")));
        assertSame(selected.get(id("item.fsh")), repaired.get(id("item.fsh")));
    }

    @Test
    void reevaluatesSelectedResourcesOnReloadAndSkipsMixedPairs() throws Exception {
        var selected = legacyEntityResources();
        var repaired = applyAdapter(selected);
        var reloaded = new HashMap<>(selected);
        reloaded.put(id("entity.vsh"), repaired.get(id("entity.vsh")));
        assertSame(reloaded, applyAdapter(reloaded));
        assertSame(repaired, applyAdapter(repaired));
        assertSame(reloaded.get(id("entity.fsh")), selected.get(id("entity.fsh")));
    }

    private static Map<Identifier, Resource> legacyEntityResources() throws IOException {
        var resources = new HashMap<Identifier, Resource>();
        resources.put(id("entity.vsh"), resource(legacy("entity.vsh")));
        resources.put(id("entity.fsh"), resource(legacy("entity.fsh")));
        return resources;
    }

    @SuppressWarnings("unchecked")
    private static Map<Identifier, Resource> applyAdapter(Map<Identifier, Resource> selected) throws Exception {
        var method = MixinShaderManager.class.getDeclaredMethod("locomotion$repairLegacyShaders", Map.class);
        method.setAccessible(true);
        return (Map<Identifier, Resource>) method.invoke(null, selected);
    }

    private static Identifier id(String shader) {
        return Identifier.withDefaultNamespace("shaders/core/" + shader);
    }

    private static Resource resource(String contents) {
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        return new Resource(PACK, () -> new ByteArrayInputStream(bytes));
    }

    private static String legacy(String shader) throws IOException {
        try (var stream = ShaderResourceSelectionTest.class.getResourceAsStream("/compat/refined-26.1/" + shader)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
