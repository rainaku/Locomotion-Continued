package com.trainguy9512.locomotion.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trainguy9512.locomotion.compat.LegacyRefinedShaders;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ShaderManager.class)
public class MixinShaderManager {
    @Unique
    private static final Logger locomotion$shaderLogger = LogManager.getLogger("Locomotion/ShaderCompat");

    @ModifyExpressionValue(method = "loadConfigs", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Lnet/minecraft/server/packs/resources/ResourceManager$Selector;)Ljava/util/Map;"))
    private static Map<Identifier, Resource> locomotion$repairLegacyShaders(Map<Identifier, Resource> selected) {
        Map<Identifier, Resource> repaired = null;
        for (String shader : List.of("entity", "item")) {
            Identifier vertexId = Identifier.withDefaultNamespace("shaders/core/" + shader + ".vsh");
            Identifier fragmentId = Identifier.withDefaultNamespace("shaders/core/" + shader + ".fsh");
            Resource vertex = selected.get(vertexId);
            Resource fragment = selected.get(fragmentId);
            if (vertex == null || fragment == null) {
                continue;
            }
            try {
                var replacement = LegacyRefinedShaders.repair(shader, vertex.readAllAsString(), fragment.readAllAsString());
                if (replacement.isPresent()) {
                    if (repaired == null) {
                        repaired = new HashMap<>(selected);
                    }
                    repaired.put(vertexId, locomotion$withSource(vertex, replacement.get().vertex()));
                    repaired.put(fragmentId, locomotion$withSource(fragment, replacement.get().fragment()));
                    locomotion$shaderLogger.info("Applied 26.3 compatibility to legacy Refined {} shaders from {} / {}",
                            shader, vertex.sourcePackId(), fragment.sourcePackId());
                }
            } catch (IOException | UncheckedIOException exception) {
                locomotion$shaderLogger.error("Could not prepare legacy Refined {} shader compatibility", shader, exception);
            }
        }
        return repaired == null ? selected : repaired;
    }

    @Unique
    private static Resource locomotion$withSource(Resource original, String source) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        return new Resource(original.source(), () -> new ByteArrayInputStream(bytes), original::metadata);
    }
}
