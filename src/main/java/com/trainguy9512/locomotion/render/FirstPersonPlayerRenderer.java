package com.trainguy9512.locomotion.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trainguy9512.locomotion.LocomotionMain;
import com.trainguy9512.locomotion.access.MatrixModelPart;
import com.trainguy9512.locomotion.animation.animator.JointAnimatorDispatcher;
import com.trainguy9512.locomotion.animation.animator.entity.firstperson.FirstPersonDrivers;
import com.trainguy9512.locomotion.animation.animator.entity.firstperson.FirstPersonJointAnimator;
import com.trainguy9512.locomotion.animation.animator.entity.firstperson.handpose.FirstPersonGenericItems;
import com.trainguy9512.locomotion.animation.animator.entity.firstperson.handpose.FirstPersonHandPoses;
import com.trainguy9512.locomotion.animation.data.AnimationDataContainer;
import com.trainguy9512.locomotion.animation.joint.JointChannel;
import com.trainguy9512.locomotion.animation.pose.ModelPartSpacePose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;

/** Extracts world-dependent item data before submitting backend-independent geometry. */
public class FirstPersonPlayerRenderer {
    private static final RenderType MAP_BACKGROUND = RenderTypes.text(Identifier.withDefaultNamespace("textures/map/map_background.png"));
    private static final RenderType MAP_BACKGROUND_CHECKERBOARD = RenderTypes.text(Identifier.withDefaultNamespace("textures/map/map_background_checkerboard.png"));
    private final ItemModelResolver itemModelResolver;
    private final MapRenderer mapRenderer;
    private final PlayerModel wideModel;
    private final PlayerModel slimModel;
    private Frame frame;

    public static boolean IS_RENDERING_LOCOMOTION_FIRST_PERSON;
    public static boolean SHOULD_FLIP_ITEM_TRANSFORM;
    public static InteractionHand CURRENT_ITEM_INTERACTION_HAND = InteractionHand.MAIN_HAND;

    public FirstPersonPlayerRenderer(EntityRendererProvider.Context context) {
        this.itemModelResolver = context.getItemModelResolver();
        this.mapRenderer = context.getMapRenderer();
        this.wideModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
        this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    public void extract(float partialTick) {
        this.frame = null;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        JointAnimatorDispatcher dispatcher = JointAnimatorDispatcher.getInstance();
        if (!LocomotionMain.CONFIG.data().firstPersonPlayer.enableRenderer || player == null || minecraft.level == null
                || dispatcher.getFirstPersonPlayerDataContainer().isEmpty() || dispatcher.getInterpolatedFirstPersonPlayerPose().isEmpty()) {
            return;
        }
        AnimationDataContainer data = dispatcher.getFirstPersonPlayerDataContainer().orElseThrow();
        ModelPartSpacePose pose = dispatcher.getInterpolatedFirstPersonPlayerPose().orElseThrow();
        // Run normal model hooks during extraction so cosmetic mods can prepare their sleeve meshes.
        var avatar = minecraft.gameRenderer.gameRenderState().levelRenderState.playerRenderState.avatarRenderState;
        if (avatar != null) {
            PlayerModel model = player.getSkin().model() == PlayerModelType.SLIM ? this.slimModel : this.wideModel;
            model.setupAnim(avatar);
        }
        JointChannel camera = pose.getJointChannel(FirstPersonJointAnimator.CAMERA_JOINT);
        Vector3f rotation = camera.getEulerRotationZYX();
        rotation.z *= -1;
        camera.rotate(rotation, JointChannel.TransformSpace.LOCAL, JointChannel.TransformType.REPLACE);
        camera.translate(camera.getTranslation().mul(1, 1, -1), JointChannel.TransformSpace.COMPONENT, JointChannel.TransformType.REPLACE);
        PoseStack cameraStack = new PoseStack();
        camera.transformPoseStack(cameraStack, 16f);
        this.frame = new Frame(player.getSkin().model(), player.getSkin().body().texturePath(), player.isInvisible(),
                player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE), player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE),
                new Matrix4f(cameraStack.last().pose()), List.of(
                        extractHand(player, data, pose, InteractionHand.OFF_HAND),
                        extractHand(player, data, pose, InteractionHand.MAIN_HAND)));
    }

    private HandFrame extractHand(LocalPlayer player, AnimationDataContainer data, ModelPartSpacePose pose, InteractionHand hand) {
        HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        Identifier handPoseId = data.getDriverValue(FirstPersonDrivers.getHandPoseDriver(hand));
        ItemRenderType renderType = handPoseId.equals(FirstPersonHandPoses.GENERIC_ITEM)
                ? FirstPersonGenericItems.getOrThrowFromIdentifier(data.getDriverValue(FirstPersonDrivers.getGenericItemPoseDriver(hand))).itemRenderType()
                : FirstPersonHandPoses.getOrThrowFromIdentifier(handPoseId).itemRenderType();
        ItemStack stack = getItemStackInHandToRender(data, player, hand);
        ItemStackRenderState itemState = new ItemStackRenderState();
        MapRenderState mapState = new MapRenderState();
        boolean hasMapData = false;
        if (renderType == ItemRenderType.MAP) {
            MapId mapId = stack.get(DataComponents.MAP_ID);
            MapItemSavedData mapData = mapId == null ? null : MapItem.getSavedData(mapId, player.level());
            if (mapData != null) {
                this.mapRenderer.extractRenderState(mapId, mapData, mapState);
                hasMapData = true;
            }
        } else {
            IS_RENDERING_LOCOMOTION_FIRST_PERSON = true;
            CURRENT_ITEM_INTERACTION_HAND = hand;
            try {
                ItemDisplayContext context = renderType.getItemDisplayContext(side);
                this.itemModelResolver.updateForTopItem(itemState, stack, context, player.level(), player, player.getId() + context.ordinal());
            } finally {
                IS_RENDERING_LOCOMOTION_FIRST_PERSON = false;
                CURRENT_ITEM_INTERACTION_HAND = InteractionHand.MAIN_HAND;
            }
        }
        PoseStack itemStack = new PoseStack();
        pose.getJointChannel(FirstPersonJointAnimator.getItemJoint(side)).transformPoseStack(itemStack, 16f);
        return new HandFrame(hand, side, new Matrix4f(pose.getJointChannel(FirstPersonJointAnimator.getArmJoint(side)).getTransform()),
                new Matrix4f(itemStack.last().pose()), itemState, mapState, hasMapData, renderType);
    }

    /** Returns false while assets/poses are unavailable so vanilla can render the hands. */
    public boolean submit(PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands) {
        Frame current = this.frame;
        if (current == null || player.avatarRenderState == null || hands.handRenderSelection == null) {
            return false;
        }
        if (hands.isScoping) {
            return true;
        }
        PlayerModel model = current.modelType == PlayerModelType.SLIM ? this.slimModel : this.wideModel;
        model.resetPose();
        model.leftSleeve.visible = current.leftSleeve;
        model.rightSleeve.visible = current.rightSleeve;
        int light = player.avatarRenderState.lightCoords;
        poseStack.pushPose();
        try {
            poseStack.rotateDegrees(Axis.ZP, 180);
            for (HandFrame hand : current.hands) {
                ModelPart arm = model.getArm(hand.side);
                ((MatrixModelPart) (Object) arm).locomotion$setMatrix(hand.armTransform);
                if (!current.invisible) {
                    poseStack.pushPose();
                    try {
                        if (current.modelType == PlayerModelType.SLIM) {
                            poseStack.translate((hand.side == HumanoidArm.LEFT ? 0.5f : -0.5f) / 16f, 0, 0);
                        }
                        collector.submitModelPart(arm, poseStack, RenderTypes.entityTranslucent(current.skin), light, OverlayTexture.NO_OVERLAY, null);
                    } finally {
                        poseStack.popPose();
                    }
                }
                poseStack.pushPose();
                IS_RENDERING_LOCOMOTION_FIRST_PERSON = true;
                SHOULD_FLIP_ITEM_TRANSFORM = hand.renderType.isMirrored() && hand.side == HumanoidArm.LEFT;
                CURRENT_ITEM_INTERACTION_HAND = hand.hand;
                try {
                    poseStack.mulPose(hand.itemTransform);
                    if (hand.renderType == ItemRenderType.MAP) {
                        submitMap(collector, poseStack, hand, light);
                    } else {
                        hand.item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
                    }
                } finally {
                    SHOULD_FLIP_ITEM_TRANSFORM = false;
                    IS_RENDERING_LOCOMOTION_FIRST_PERSON = false;
                    CURRENT_ITEM_INTERACTION_HAND = InteractionHand.MAIN_HAND;
                    poseStack.popPose();
                }
            }
        } finally {
            poseStack.popPose();
        }
        return true;
    }

    private void submitMap(SubmitNodeCollector collector, PoseStack poseStack, HandFrame hand, int light) {
        poseStack.scale(-1f / 16f, 1f / 16f, -1f / 16f);
        poseStack.translate(-2, -4, -1);
        poseStack.scale(1f / 32f, 1f / 32f, 1f / 32f);
        collector.submitCustomGeometry(poseStack, hand.hasMapData ? MAP_BACKGROUND_CHECKERBOARD : MAP_BACKGROUND, (pose, vertices) -> {
            vertices.addVertex(pose, -7, 135, 0).setColor(-1).setUv(0, 1).setLight(light);
            vertices.addVertex(pose, 135, 135, 0).setColor(-1).setUv(1, 1).setLight(light);
            vertices.addVertex(pose, 135, -7, 0).setColor(-1).setUv(1, 0).setLight(light);
            vertices.addVertex(pose, -7, -7, 0).setColor(-1).setUv(0, 0).setLight(light);
        });
        if (hand.hasMapData) {
            this.mapRenderer.render(hand.map, poseStack, collector, false, light);
        }
    }

    public void transformCamera(PoseStack poseStack) {
        Frame current = this.frame;
        if (current != null) {
            poseStack.mulPose(current.cameraTransform);
        }
    }

    public boolean isReady() {
        return this.frame != null;
    }

    private static ItemStack getItemStackInHandToRender(AnimationDataContainer data, LocalPlayer player, InteractionHand hand) {
        ItemStack rendered = data.getDriverValue(FirstPersonDrivers.getRenderedItemDriver(hand));
        ItemStack actual = player.getItemInHand(hand);
        if (!ItemStack.isSameItem(actual, rendered)) {
            return rendered;
        }
        if (ItemStack.isSameItemSameComponents(actual, rendered)) {
            return actual;
        }
        for (TypedDataComponent<?> component : actual.getComponents()) {
            if (component.type() != DataComponents.DAMAGE && !Objects.equals(rendered.get(component.type()), component.value())) {
                return rendered;
            }
        }
        return actual;
    }

    private record Frame(PlayerModelType modelType, Identifier skin, boolean invisible, boolean leftSleeve, boolean rightSleeve,
                         Matrix4f cameraTransform, List<HandFrame> hands) {}
    private record HandFrame(InteractionHand hand, HumanoidArm side, Matrix4f armTransform, Matrix4f itemTransform,
                             ItemStackRenderState item, MapRenderState map, boolean hasMapData, ItemRenderType renderType) {}
}
