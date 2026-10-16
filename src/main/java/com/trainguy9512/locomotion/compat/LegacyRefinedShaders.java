package com.trainguy9512.locomotion.compat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Adapts the known 26.1 Refined shaders without overriding other packs' shaders. */
public final class LegacyRefinedShaders {
    private static final Map<String, Fingerprints> LEGACY = Map.of(
            "entity", new Fingerprints(
                    "68268211770ac115b7cdd59a007d489c82535319d168073aee7eadcc07b9eb9f",
                    "47a5d50ba5de9a1a2fdb8a6654778238730d0df02f43ee313a84a63536b7c124"),
            "item", new Fingerprints(
                    "2cae8ef2e60f6507d8a89f0aec2f7580da449867ea4e7ef979435fd25057ea0d",
                    "ef24e9713b5cb5b988ad51902bf8a4306c2cf9bfb99b224e97a05736cccc0556"));
    private static final Map<String, Sources> REPLACEMENTS = new ConcurrentHashMap<>();

    private LegacyRefinedShaders() {}

    public static Optional<Sources> repair(String shader, String vertex, String fragment) {
        Fingerprints expected = LEGACY.get(shader);
        // Check the selected pair together. A higher-priority custom/modern shader
        // must never be combined with half of this compatibility implementation.
        if (expected == null || !vertex.contains("#moj_import") || !fragment.contains("#moj_import")
                || !fingerprint(vertex).equals(expected.vertex()) || !fingerprint(fragment).equals(expected.fragment())) {
            return Optional.empty();
        }
        return Optional.of(REPLACEMENTS.computeIfAbsent(shader,
                name -> new Sources(readReplacement(name + ".vsh"), readReplacement(name + ".fsh"))));
    }

    private static String fingerprint(String source) {
        try {
            byte[] normalized = source.replace("\r\n", "\n").strip().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static String readReplacement(String file) {
        String path = "/assets/locomotion/compat/refined_shaders/" + file;
        try (var stream = LegacyRefinedShaders.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing bundled shader compatibility source: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public record Sources(String vertex, String fragment) {}

    private record Fingerprints(String vertex, String fragment) {}
}
