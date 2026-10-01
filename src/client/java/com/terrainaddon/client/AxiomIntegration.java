package com.terrainaddon.client;

import com.moulberry.axiomclientapi.service.RegionProvider;
import com.moulberry.axiomclientapi.service.ToolRegistryService;
import com.moulberry.axiomclientapi.service.ToolService;
import com.terrainaddon.client.terrain.TerrainTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Discovers Axiom's addon services at runtime and, if present, registers this addon's terrain
 * brush with Axiom's tool menu. Using {@link ServiceLoader} means the mod never hard-depends on
 * Axiom: without it the addon simply registers nothing and logs a warning.
 *
 * <p>This is Axiom's supported third-party addon entrypoint on MC 1.21.10 (Axiom 5.4.2). See the
 * project README for why the {@code com.moulberry.axiomclientapi} service model is used rather
 * than the internal {@code com.moulberry.axiom.AxiomAddon} module system.</p>
 */
public final class AxiomIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger("terrain-addon");

    private static ToolService toolService;
    private static RegionProvider regionProvider;

    private AxiomIntegration() {
    }

    public static void registerTerrainTool() {
        Optional<ToolRegistryService> registry = ServiceLoader.load(ToolRegistryService.class).findFirst();
        Optional<ToolService> tools = ServiceLoader.load(ToolService.class).findFirst();
        Optional<RegionProvider> regions = ServiceLoader.load(RegionProvider.class).findFirst();

        if (registry.isEmpty() || tools.isEmpty() || regions.isEmpty()) {
            LOGGER.warn("Axiom was not found; the terrain generator was not registered. "
                    + "Install Axiom 5.4.2 for Minecraft 1.21.10 to use it.");
            return;
        }

        toolService = tools.get();
        regionProvider = regions.get();

        registry.get().register(new TerrainTool());
        LOGGER.info("Registered Terrain Generator with Axiom.");
    }

    /** Axiom's tool service: raycasting, committing edits (undoable), etc. */
    public static ToolService toolService() {
        return toolService;
    }

    /** Axiom's region factory, used to build ghost previews and committed edits. */
    public static RegionProvider regionProvider() {
        return regionProvider;
    }
}