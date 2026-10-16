package com.trainguy9512.locomotion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.trainguy9512.locomotion.access.MatrixModelPart;
import com.trainguy9512.locomotion.access.FirstPersonPlayerRendererGetter;
import com.trainguy9512.locomotion.resource.LocomotionResources;
import com.trainguy9512.locomotion.config.LocomotionConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocomotionClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            String expected = System.getProperty("locomotion.test.backend", "opengl");
            String actual = RenderSystem.getDevice().getDeviceInfo().backendName();
            if (!expected.equalsIgnoreCase(actual)) {
                throw new AssertionError("Requested " + expected + " but Minecraft selected " + actual);
            }
            if (LocomotionResources.getJointSkeletons().size() < 4 || LocomotionResources.getAnimationSequences().isEmpty()) {
                throw new AssertionError("Animation resources failed to load");
            }
            ModelPart part = new ModelPart(List.of(), Map.of());
            Matrix4f transform = new Matrix4f().translation(16, 32, 48);
            ((MatrixModelPart) (Object) part).locomotion$setMatrix(transform);
            for (int i = 0; i < 3; i++) {
                PoseStack stack = new PoseStack();
                part.translateAndRotate(stack);
                if (stack.last().pose().m30() != 1 || stack.last().pose().m31() != 2 || stack.last().pose().m32() != 3) {
                    throw new AssertionError("Rendering changed the stored animation transform");
                }
            }
            if (transform.m30() != 16) throw new AssertionError("Animation transform was mutated");
            checkConfigRecovery();
        });
        var parent = context.computeOnClient(client -> client.gui.screen());
        context.runOnClient(client -> client.gui.setScreen(LocomotionMain.CONFIG.getConfigScreen(FabricLoader.getInstance()::isModLoaded).apply(parent)));
        context.waitTicks(5);
        context.runOnClient(client -> client.gui.setScreen(parent));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.runOnClient(client -> {
                boolean ready = ((FirstPersonPlayerRendererGetter) client.getEntityRenderDispatcher()).locomotion$getFirstPersonPlayerRenderer()
                        .map(renderer -> renderer.isReady()).orElse(false);
                if (!ready) throw new AssertionError("Locomotion first-person renderer did not produce a frame");
            });
            world.getServer().runCommand("gamemode creative @a");
            BlockPos chest = context.computeOnClient(client -> client.player.blockPosition().offset(0, 0, 3));
            BlockPos shulker = chest.offset(2, 0, 0);
            world.getServer().runCommand("setblock " + coordinates(chest) + " minecraft:chest");
            world.getServer().runCommand("setblock " + coordinates(shulker) + " minecraft:shulker_box");
            context.runOnClient(client -> {
                client.player.setYRot(0);
                client.player.setXRot(0);
                client.options.renderDistance().set(5);
            });
            context.waitTicks(15);
            for (String item : List.of("diamond_sword", "bow", "crossbow", "trident", "spyglass", "shield", "fishing_rod", "filled_map", "chest", "shulker_box", "apple")) {
                world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:" + item);
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(24);
                context.runOnClient(client -> client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND));
                context.waitTicks(8);
                context.runOnClient(client -> client.gameMode.releaseUsingItem(client.player));
                context.waitTicks(4);
            }
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:shield");
            context.runOnClient(client -> client.options.mainHand().set(HumanoidArm.LEFT));
            context.waitTicks(20);
            context.runOnClient(client -> client.options.mainHand().set(HumanoidArm.RIGHT));
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
            for (BlockPos position : List.of(chest, shulker)) {
                context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false)));
                context.waitTicks(12);
                context.runOnClient(client -> client.player.closeContainer());
                world.getServer().runOnServer(server -> server.overworld().destroyBlockProgress(999, position, 5));
                context.waitTicks(12);
                world.getServer().runOnServer(server -> server.overworld().destroyBlockProgress(999, position, -1));
            }
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(12);
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            context.runOnClient(client -> {
                LocomotionMain.CONFIG.data().firstPersonPlayer.enableRenderer = false;
                LocomotionMain.CONFIG.save();
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                if (((FirstPersonPlayerRendererGetter) client.getEntityRenderDispatcher()).locomotion$getFirstPersonPlayerRenderer().orElseThrow().isReady()) {
                    throw new AssertionError("Disabled renderer retained its frame");
                }
                LocomotionMain.CONFIG.data().firstPersonPlayer.enableRenderer = true;
                LocomotionMain.CONFIG.save();
            });
            context.waitTicks(20);
            var reload = context.computeOnClient(client -> client.delayTextureReload());
            context.waitFor(client -> reload.isDone());
            reload.join();
            context.waitFor(client -> client.gui.overlay() == null);
            context.waitTicks(20);
            System.out.println("LOCOMOTION_SCREENSHOT " + context.takeScreenshot("locomotion-first-person"));
        }
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
        }
        System.out.println("LOCOMOTION_TEST_PASSED backend=" + System.getProperty("locomotion.test.backend"));
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }

    private static void checkConfigRecovery() {
        Path config = Path.of("config", "locomotion.json");
        try {
            String original = Files.readString(config);
            try {
                Files.writeString(config, "null");
                LocomotionConfig nullable = new LocomotionConfig();
                nullable.load();
                if (nullable.data() == null) throw new AssertionError("Null config did not recover");
                Files.writeString(config, "{\"first_person_player\":null,\"block_entities\":{\"enabled_block_entities\":null,\"pose_calculation_frequency\":null}}");
                nullable.load();
                if (nullable.data().firstPersonPlayer == null || nullable.data().blockEntities.enabledBlockEntities == null) {
                    throw new AssertionError("Null config sections did not recover");
                }
                Files.writeString(config, "{invalid");
                nullable.load();
                if (!Files.readString(config).equals("{invalid")) throw new AssertionError("Malformed config was overwritten");
            } finally {
                Files.writeString(config, original);
                LocomotionMain.CONFIG.load();
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
