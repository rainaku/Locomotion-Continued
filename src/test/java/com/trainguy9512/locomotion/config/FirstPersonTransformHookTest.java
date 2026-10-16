package com.trainguy9512.locomotion.config;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the real 26.3 call sites without launching Minecraft or a GPU context. */
class FirstPersonTransformHookTest {
    @Test
    void bothVanillaItemSubmissionBranchesHaveTheExpectedHook() throws IOException {
        assertEquals(2, calls("submitArmWithItem", "net/minecraft/client/renderer/item/ItemStackRenderState", "submit",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"));
    }

    @Test
    void bothVanillaMapPathsHaveTheExpectedHook() throws IOException {
        String owner = "net/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer";
        String descriptor = "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/item/ItemStack;ZLnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V";
        assertEquals(1, calls("renderOneHandedMap", owner, "renderMap", descriptor));
        assertEquals(1, calls("renderTwoHandedMap", owner, "renderMap", descriptor));
    }

    private static int calls(String method, String owner, String invokedMethod, String descriptor) throws IOException {
        try (var stream = FirstPersonHandsAndItemsRenderer.class.getResourceAsStream("FirstPersonHandsAndItemsRenderer.class")) {
            assertNotNull(stream);
            var model = ClassFile.of().parse(stream.readAllBytes());
            var target = model.methods().stream().filter(m -> m.methodName().stringValue().equals(method)).findFirst().orElseThrow();
            int count = 0;
            for (var element : target.code().orElseThrow()) {
                if (element instanceof InvokeInstruction call && call.owner().asInternalName().equals(owner)
                        && call.name().stringValue().equals(invokedMethod) && call.type().stringValue().equals(descriptor)) count++;
            }
            return count;
        }
    }
}
