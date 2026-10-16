package com.trainguy9512.locomotion.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trainguy9512.locomotion.access.EntityRenderStateAccess;
import com.trainguy9512.locomotion.render.LocomotionWrappedRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelFeatureRenderer.class)
public class MixinModelFeatureRenderer {
    @WrapOperation(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
    private void locomotion$setupAnim(Model<?> model, Object state, Operation<Void> original) {
        if (state instanceof LocomotionWrappedRenderState<?> wrapped) {
            original.call(model, wrapped.innerValue());
            wrapped.pose().setupAnimOnModel(model);
        } else {
            original.call(model, state);
            if (state instanceof EntityRenderState entityState) {
                ((EntityRenderStateAccess) entityState).animationOverhaul$getInterpolatedAnimationPose().ifPresent(pose -> pose.setupAnimOnModel(model));
            }
        }
    }
}
