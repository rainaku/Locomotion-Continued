package com.trainguy9512.locomotion.mixin.render.block_entity;

import com.trainguy9512.locomotion.access.BlockEntityPoseAccess;
import com.trainguy9512.locomotion.animation.pose.ModelPartSpacePose;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntityRenderState.class)
public class MixinBlockEntityRenderState implements BlockEntityPoseAccess {
    @Unique private ModelPartSpacePose locomotion$pose;

    public ModelPartSpacePose locomotion$getPose() { return this.locomotion$pose; }
    public void locomotion$setPose(ModelPartSpacePose pose) { this.locomotion$pose = pose; }
}
