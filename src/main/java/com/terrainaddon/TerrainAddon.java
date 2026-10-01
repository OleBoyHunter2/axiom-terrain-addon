package com.terrainaddon;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal main entrypoint. All of the actual behaviour of this addon is client-side and is
 * registered from {@link com.terrainaddon.client.TerrainAddonClient}, which discovers Axiom's
 * addon services via {@link java.util.ServiceLoader}.
 */
public final class TerrainAddon implements ModInitializer {

    public static final String MOD_ID = "terrain-addon";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Terrain Generator addon initialised.");
    }
}