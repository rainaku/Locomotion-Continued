package com.trainguy9512.locomotion.config;

import com.google.common.collect.Maps;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.trainguy9512.locomotion.LocomotionMain;
import com.trainguy9512.locomotion.animation.animator.JointAnimator;
import com.trainguy9512.locomotion.animation.animator.JointAnimatorDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class LocomotionConfig {

    private static final Logger LOGGER = LogManager.getLogger("Locomotion/Config");

    //private static final Path CONFIG_FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve(LocomotionMain.MOD_ID + ".json");
    private static final Path CONFIG_FILE_PATH = Path.of("config").resolve( LocomotionMain.MOD_ID + ".json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    private Data configData;
    private HeldItemModelSettings heldItemPreview;

    public LocomotionConfig() {
        this.configData = new Data();
    }

    public Data data() {
        return configData;
    }

    public HeldItemModelSettings heldItemModelsForRendering() {
        return heldItemPreview != null ? heldItemPreview : configData.firstPersonPlayer.heldItemModels;
    }

    public boolean isHeldItemPreviewActive() {
        return heldItemPreview != null;
    }

    public void beginHeldItemPreview(HeldItemModelSettings draft) {
        heldItemPreview = draft;
    }

    public void endHeldItemPreview(HeldItemModelSettings draft) {
        if (heldItemPreview == draft) heldItemPreview = null;
    }

    public void load() {
        this.configData = new Data();
        if (Files.exists(CONFIG_FILE_PATH)) {
            try (FileReader reader = new FileReader(CONFIG_FILE_PATH.toFile())) {
                Data loaded = GSON.fromJson(reader, LocomotionConfig.Data.class);
                if (loaded != null) {
                    if (loaded.firstPersonPlayer == null) loaded.firstPersonPlayer = new Data.FirstPersonPlayer();
                    if (loaded.firstPersonPlayer.heldItemModels == null) loaded.firstPersonPlayer.heldItemModels = new HeldItemModelSettings();
                    loaded.firstPersonPlayer.heldItemModels.sanitize();
                    if (loaded.blockEntities == null) loaded.blockEntities = new Data.BlockEntities();
                    if (loaded.blockEntities.enabledBlockEntities == null) loaded.blockEntities.enabledBlockEntities = Maps.newHashMap();
                    if (loaded.blockEntities.poseCalculationFrequency == null) loaded.blockEntities.poseCalculationFrequency = JointAnimator.PoseCalculationFrequency.CALCULATE_EVERY_FRAME;
                    this.configData = loaded;
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("Failed to load config {}; using defaults", CONFIG_FILE_PATH.toAbsolutePath(), e);
                JointAnimatorDispatcher.getInstance().reInitializeData();
                return;
            }
        }
        save();

    }

    public void save() {
        this.configData.firstPersonPlayer.heldItemModels.sanitize();
        try {
            Files.createDirectories(CONFIG_FILE_PATH.toAbsolutePath().getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE_PATH)) {
                writer.write(GSON.toJson(this.configData));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to write config to path {}", CONFIG_FILE_PATH.toAbsolutePath());
        }
        JointAnimatorDispatcher.getInstance().reInitializeData();
    }


    public static class Data {

        public FirstPersonPlayer firstPersonPlayer = new FirstPersonPlayer();
        public BlockEntities blockEntities = new BlockEntities();

        public static class FirstPersonPlayer {

            public HeldItemModelSettings heldItemModels = new HeldItemModelSettings();

            public boolean enableRenderer = true;

            public boolean enableCameraRotationDamping = true;
            public float cameraRotationStiffnessFactor = 0.3f;
            public float cameraRotationDampingFactor = 0.65f;

            public float cameraShakeMasterIntensity = 1f;
            public float cameraShakeMovementIntensity = 1f;
            public float cameraShakeItemInteractionIntensity = 0.5f;

            public float miningAnimationSpeedMultiplier = 1f;
            public float mapMovementAnimationIntensity = 0.2f;
            public float runningArmSwingIntensity = 1f;

            public float armOffsetX = 0f;
            public float armOffsetY = 0f;
            public float armOffsetZ = 0f;

        }

        public static class BlockEntities {

            public JointAnimator.PoseCalculationFrequency poseCalculationFrequency = JointAnimator.PoseCalculationFrequency.CALCULATE_EVERY_FRAME;
            public int evaluationDistance = 32;
            public Map<String, Boolean> enabledBlockEntities = Maps.newHashMap();

        }
    }

    public Function<Screen, Screen> getConfigScreen(Predicate<String> ifModLoaded) {
        return LocomotionConfigScreen::createConfigScreen;
    }
}
