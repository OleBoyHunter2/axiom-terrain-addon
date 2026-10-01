package com.terrainaddon.client;

import net.fabricmc.api.ClientModInitializer;

public final class TerrainAddonClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AxiomIntegration.registerTerrainTool();
    }
}