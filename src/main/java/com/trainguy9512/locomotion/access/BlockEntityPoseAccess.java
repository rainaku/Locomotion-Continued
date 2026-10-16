package com.trainguy9512.locomotion.access;

import com.trainguy9512.locomotion.animation.pose.ModelPartSpacePose;

public interface BlockEntityPoseAccess {
    ModelPartSpacePose locomotion$getPose();
    void locomotion$setPose(ModelPartSpacePose pose);
}
