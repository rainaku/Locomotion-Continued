package com.trainguy9512.locomotion.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.trainguy9512.locomotion.LocomotionMain;
import com.trainguy9512.locomotion.access.FirstPersonPlayerRendererGetter;
import com.trainguy9512.locomotion.animation.animator.JointAnimatorDispatcher;
import com.trainguy9512.locomotion.animation.animator.JointAnimatorRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "extract", at = @At("HEAD"))
    private void locomotion$extractPose(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (LocomotionMain.CONFIG.data().firstPersonPlayer.enableRenderer && this.minecraft.player != null) {
            JointAnimatorDispatcher dispatcher = JointAnimatorDispatcher.getInstance();
            dispatcher.getFirstPersonPlayerDataContainer().ifPresent(data ->
                    JointAnimatorRegistry.getFirstPersonPlayerJointAnimator().ifPresent(animator ->
                            dispatcher.calculateInterpolatedFirstPersonPlayerPose(data, deltaTracker.getGameTimeDeltaPartialTick(true))));
        }
    }

    @Inject(method = "extract", at = @At("TAIL"))
    private void locomotion$extractItems(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ((FirstPersonPlayerRendererGetter) this.minecraft.getEntityRenderDispatcher()).locomotion$getFirstPersonPlayerRenderer()
                .ifPresent(renderer -> renderer.extract(deltaTracker.getGameTimeDeltaPartialTick(true)));
    }

    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void locomotion$transformCamera(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
        if (camera.isFirstPerson) {
            ((FirstPersonPlayerRendererGetter) this.minecraft.getEntityRenderDispatcher()).locomotion$getFirstPersonPlayerRenderer()
                    .ifPresent(renderer -> renderer.transformCamera(poseStack));
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void locomotion$removeViewBobbing(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
        if (camera.isFirstPerson) {
            ((FirstPersonPlayerRendererGetter) this.minecraft.getEntityRenderDispatcher()).locomotion$getFirstPersonPlayerRenderer()
                    .filter(com.trainguy9512.locomotion.render.FirstPersonPlayerRenderer::isReady).ifPresent(renderer -> ci.cancel());
        }
    }
}
