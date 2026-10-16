package com.trainguy9512.locomotion.render;

import com.trainguy9512.locomotion.access.BlockEntityPoseAccess;
import com.trainguy9512.locomotion.animation.animator.JointAnimatorDispatcher;
import com.trainguy9512.locomotion.animation.pose.ModelPartSpacePose;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Carries a sampled pose through deferred model and breaking-overlay rendering. */
public record LocomotionWrappedRenderState<D>(D innerValue, ModelPartSpacePose pose) {
    public static Object wrap(Object value, ModelPartSpacePose pose) {
        return pose == null ? value : new LocomotionWrappedRenderState<>(value, pose);
    }

    public static void extract(BlockEntity entity, BlockEntityRenderState state, float partialTick) {
        ModelPartSpacePose pose = entity.hasLevel() ? JointAnimatorDispatcher.getInstance()
                .getBlockEntityAnimationDataContainer(entity.getBlockPos(), entity.getType())
                .map(data -> data.getInterpolatedAnimationPose(partialTick)).orElse(null) : null;
        ((BlockEntityPoseAccess) state).locomotion$setPose(pose);
    }
}
