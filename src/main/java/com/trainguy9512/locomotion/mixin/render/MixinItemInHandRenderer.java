package com.trainguy9512.locomotion.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trainguy9512.locomotion.LocomotionMain;
import com.trainguy9512.locomotion.access.FirstPersonPlayerRendererGetter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

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

    // This method runs only when vanilla handles the hand. The Locomotion path
    // cancels submitHandsWithItems and already captures its transform in extraction.
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void locomotion$transformVanillaItem(ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector,
                                                int light, int overlay, int outline, Operation<Void> original,
                                                @Local(argsOnly = true) ItemStack stack,
                                                @Local(argsOnly = true) InteractionHand hand,
                                                @Local(argsOnly = true) PlayerRenderState player) {
        boolean left = player.avatarRenderState != null
                && (hand == InteractionHand.MAIN_HAND ? player.avatarRenderState.mainArm : player.avatarRenderState.mainArm.getOpposite()) == HumanoidArm.LEFT;
        poseStack.pushPose();
        try {
            locomotion$applyTransform(poseStack, stack, left);
            original.call(item, poseStack, collector, light, overlay, outline);
        } finally {
            poseStack.popPose();
        }
    }

    @WrapOperation(method = {"renderOneHandedMap", "renderTwoHandedMap"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderMap(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/item/ItemStack;ZLnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"))
    private void locomotion$transformVanillaMap(FirstPersonHandsAndItemsRenderer renderer, PoseStack poseStack, SubmitNodeCollector collector,
                                               int light, ItemStack stack, boolean mainHand, FirstPersonHandsAndItemsRenderState hands,
                                               Operation<Void> original, @Local(argsOnly = true) PlayerRenderState player) {
        boolean left = player.avatarRenderState != null
                && (mainHand ? player.avatarRenderState.mainArm : player.avatarRenderState.mainArm.getOpposite()) == HumanoidArm.LEFT;
        poseStack.pushPose();
        try {
            locomotion$applyTransform(poseStack, stack, left);
            original.call(renderer, poseStack, collector, light, stack, mainHand, hands);
        } finally {
            poseStack.popPose();
        }
    }

    @Unique
    private static void locomotion$applyTransform(PoseStack poseStack, ItemStack stack, boolean left) {
        if (!stack.isEmpty()) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            poseStack.mulPose(LocomotionMain.CONFIG.heldItemModelsForRendering().resolve(itemId).applyTo(new Matrix4f(), left));
        }
    }
}
