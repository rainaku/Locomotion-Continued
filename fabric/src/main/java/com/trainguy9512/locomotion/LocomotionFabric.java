package com.trainguy9512.locomotion;

import com.trainguy9512.locomotion.debug.DebugEntryFirstPersonDrivers;
import com.trainguy9512.locomotion.debug.LocomotionDebugScreenEntries;
import com.trainguy9512.locomotion.resource.LocomotionResources;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.trainguy9512.locomotion.animation.animator.JointAnimatorDispatcher;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public class LocomotionFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LocomotionMain.initialize();
        registerResourceReloader();
        registerDebugEntries();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> JointAnimatorDispatcher.getInstance().reInitializeData());
    }

    private static void registerResourceReloader() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(LocomotionResources.RELOADER_IDENTIFIER, new LocomotionResources());
    }

    private static void registerDebugEntries() {
        LocomotionDebugScreenEntries.register(DebugScreenEntries::register);
    }
}
