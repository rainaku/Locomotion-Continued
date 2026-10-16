package com.trainguy9512.locomotion.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.trainguy9512.locomotion.access.FirstPersonPlayerRendererGetter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class MixinItemInHandRenderer {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void locomotion$submitHands(float partialTick, PoseStack poseStack, SubmitNodeCollector collector,
                                       PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
        ((FirstPersonPlayerRendererGetter) Minecraft.getInstance().getEntityRenderDispatcher())
                .locomotion$getFirstPersonPlayerRenderer().ifPresent(renderer -> {
                    if (renderer.submit(poseStack, collector, player, hands)) {
                        ci.cancel();
                    }
                });
    }
}
