package com.trainguy9512.locomotion.compat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRefinedShadersTest {
    @ParameterizedTest
    @ValueSource(strings = {"entity", "item"})
    void repairsTheActualLegacySources(String shader) throws IOException {
        var repaired = LegacyRefinedShaders.repair(shader, legacy(shader, ".vsh"), legacy(shader, ".fsh")).orElseThrow();
        assertFalse(repaired.vertex().contains("#moj_import"));
        assertFalse(repaired.fragment().contains("#moj_import"));
        assertTrue(repaired.vertex().contains("layout(location = 0) in vec3 Position;"));
        assertTrue(repaired.fragment().contains("#include <minecraft:oit.glsl>"));
        assertTrue(repaired.fragment().contains("refinedEmissive"));
        assertTrue(repaired.fragment().contains("alphaTag == 254"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"entity", "item"})
    void toleratesLineEndingsAndOuterWhitespace(String shader) throws IOException {
        String vertex = "\r\n" + legacy(shader, ".vsh").replace("\n", "\r\n") + "\r\n ";
        String fragment = "\n " + legacy(shader, ".fsh") + "\n";
        assertTrue(LegacyRefinedShaders.repair(shader, vertex, fragment).isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"entity", "item"})
    void leavesModernShadersAndMixedPairsAlone(String shader) throws IOException {
        String vertex = legacy(shader, ".vsh");
        String fragment = legacy(shader, ".fsh");
        var modern = LegacyRefinedShaders.repair(shader, vertex, fragment).orElseThrow();
        assertTrue(LegacyRefinedShaders.repair(shader, modern.vertex(), modern.fragment()).isEmpty());
        assertTrue(LegacyRefinedShaders.repair(shader, modern.vertex(), fragment).isEmpty());
        assertTrue(LegacyRefinedShaders.repair(shader, vertex, modern.fragment()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"entity", "item"})
    void leavesCustomLegacyShadersAlone(String shader) throws IOException {
        String vertex = legacy(shader, ".vsh");
        String fragment = legacy(shader, ".fsh");
        assertTrue(LegacyRefinedShaders.repair(shader, vertex + "\n// custom shader", fragment).isEmpty());
        assertTrue(LegacyRefinedShaders.repair(shader, vertex, fragment + "\n// custom shader").isEmpty());
        assertTrue(LegacyRefinedShaders.repair("other", vertex, fragment).isEmpty());
    }

    private static String legacy(String shader, String extension) throws IOException {
        try (var stream = LegacyRefinedShadersTest.class.getResourceAsStream("/compat/refined-26.1/" + shader + extension)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
