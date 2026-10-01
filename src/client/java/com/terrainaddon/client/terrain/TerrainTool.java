package com.terrainaddon.client.terrain;

import com.moulberry.axiomclientapi.CustomTool;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.IAxiomWorldRenderContext;
import com.moulberry.axiomclientapi.regions.BlockRegion;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import com.terrainaddon.client.AxiomIntegration;
import imgui.moulberry92.ImGui;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The terrain-generation brush, registered into Axiom's tool menu via
 * {@link com.moulberry.axiomclientapi.service.ToolRegistryService}. It behaves exactly like any
 * other native Axiom brush:
 *
 * <ul>
 *   <li><b>Right-click</b> an anchor point to place the region;</li>
 *   <li>change the settings in Axiom's ImGui options panel and the ghost preview updates live;</li>
 *   <li><b>Enter</b> commits the previewed blocks into the world via Axiom's
 *       {@link com.moulberry.axiomclientapi.service.ToolService#pushBlockRegionChange BlockRegion}
 *       pipeline (undoable);</li>
 *   <li><b>Delete</b> (or re-selecting the tool) clears the preview with no world change.</li>
 * </ul>
 *
 * <p>MC 26.1 edition: compiled against Axiom 5.4.2's 'unobf' addon-api with Mojang official
 * mappings, so all Minecraft types below use mojmap names and the render callback takes the new
 * {@link IAxiomWorldRenderContext}.</p>
 */
public final class TerrainTool implements CustomTool {

    /** Above this cell budget the ghost preview falls back to a light top-surface overlay,
     *  keeping per-frame translucent-cell counts reasonable. */
    private static final long PREVIEW_VOLUME_BUDGET = 65_536L;

    private final TerrainSettings settings = new TerrainSettings();
    private final TerrainGenerator generator = new TerrainGenerator();

    private BlockPos anchor;
    private BooleanRegion previewRegion;
    private boolean previewDirty = true;

    @Override
    public String name() {
        return "Terrain Generator";
    }

    @Override
    public void reset() {
        anchor = null;
        if (previewRegion != null) {
            previewRegion.close();
            previewRegion = null;
        }
        previewDirty = true;
    }

    /**
     * Anchor the region at the raycast target. Only a mirror of the WorldEdit "selection": the
     * selected volume is the anchor-centred, configurable {@code width x depth x height} box
     * (Axiom's public addon API exposes no read-only "current selection").
     */
    @Override
    public boolean callUseTool() {
        BlockHitResult hit = AxiomIntegration.toolService().raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos p = hit.getBlockPos();
        // Anchor on the surface above the hit block when we looked at its top face, like the
        // WorldEdit region would sit at the selection's minimum corner.
        this.anchor = hit.getDirection() == Direction.UP ? p.above() : p;
        this.previewDirty = true;
        return true;
    }

    /** Build the previewed blocks into the world (undoable) via Axiom's history system. */
    @Override
    public boolean callConfirm() {
        if (anchor == null) {
            return false;
        }
        BlockRegion region = AxiomIntegration.regionProvider().createBlock();
        fillRegion(region);
        if (!region.isEmpty()) {
            AxiomIntegration.toolService().pushBlockRegionChange(region);
        }
        reset();
        return true;
    }

    /** Cancel: clear the ghost preview, make no world change. */
    @Override
    public boolean callDelete() {
        if (anchor == null) {
            return false;
        }
        reset();
        return true;
    }

    @Override
    public void render(IAxiomWorldRenderContext context) {
        if (anchor == null) {
            return;
        }
        if (previewDirty || previewRegion == null) {
            rebuildPreview();
        }
        if (previewRegion != null) {
            // Axiom's own ghost-preview renderer; SELECTION = translucent fill + outline.
            // The Vec3 is a camera-relative offset (the render pipeline already translates by
            // the camera position from the context), so ZERO keeps the region world-anchored.
            previewRegion.render(context, Vec3.ZERO, Effects.SELECTION);
        }
    }

    /** Renders Axiom's options panel for this tool. */
    @Override
    public void displayImguiOptions() {
        if (anchor == null) {
            ImGui.textWrapped("Right-click the ground to place the terrain region. The terrain is filled "
                    + "with your Active Block (pick one in Axiom's block palette). Tweak the settings for "
                    + "a live ghost preview; press Enter to build, Delete to cancel.");
        } else {
            ImGui.textWrapped("Ghost preview active. Change settings to update it live. "
                    + "Press Enter to commit (undoable) or Delete to cancel.");
        }
        ImGui.separator();

        boolean changed = settings.render();

        ImGui.separator();
        if (ImGui.button("Randomize Seed")) {
            generator.setSeed(System.currentTimeMillis());
            changed = true;
        }
        if (ImGui.button("Clear Preview")) {
            reset();
            changed = true;
        }

        if (changed) {
            previewDirty = true;
        }
    }

    // ----------------------------------------------------------------------------------
    // Preview + commit construction
    // ----------------------------------------------------------------------------------

    /** Region half-extents so the brush footprint is centred on the anchor. */
    private int minX() {
        return anchor.getX() - settings.width[0] / 2;
    }

    private int minZ() {
        return anchor.getZ() - settings.depth[0] / 2;
    }

    private int maxX() {
        return minX() + settings.width[0] - 1;
    }

    private int maxZ() {
        return minZ() + settings.depth[0] - 1;
    }

    /** Column height above the anchor Y for (x, z); inclusive top Y of the solid fill. */
    private int columnTopY(int x, int z) {
        int baseY = anchor.getY();
        int h = settings.height[0];
        double n = generator.normalizedHeight(x, z, settings.noiseType(),
                settings.scale[0], settings.octaves[0], settings.roughness[0]);
        // Mirror the original generator exactly: baseY + (int)(normalisedNoise * height).
        int top = baseY + (int) (n * h);
        return Math.max(baseY, Math.min(top, baseY + h));
    }

    private void fillRegion(BlockRegion region) {
        int maxY = anchor.getY() + settings.height[0];
        BlockState target = settings.block();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = minX(); x <= maxX(); x++) {
            for (int z = minZ(); z <= maxZ(); z++) {
                int top = columnTopY(x, z);
                for (int y = anchor.getY(); y <= top; y++) {
                    region.addBlock(x, y, z, target);
                }
                for (int y = top + 1; y <= maxY; y++) {
                    region.addBlock(x, y, z, air);
                }
            }
        }
    }

    private void rebuildPreview() {
        if (previewRegion != null) {
            previewRegion.close();
        }
        previewRegion = AxiomIntegration.regionProvider().createBoolean();

        long columns = (long) settings.width[0] * settings.depth[0];
        long cellBudget = columns * settings.height[0];
        boolean fullVolume = cellBudget <= PREVIEW_VOLUME_BUDGET;

        for (int x = minX(); x <= maxX(); x++) {
            for (int z = minZ(); z <= maxZ(); z++) {
                int top = columnTopY(x, z);
                if (fullVolume) {
                    for (int y = anchor.getY(); y <= top; y++) {
                        previewRegion.add(x, y, z);
                    }
                } else if (top > anchor.getY()) {
                    // Cheap silhouette for large regions: ghost just the terrain surface.
                    previewRegion.add(x, top, z);
                }
            }
        }
        previewDirty = false;
    }
}