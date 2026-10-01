package com.terrainaddon.client.terrain;

import com.moulberry.axiomclientapi.service.ToolService;
import com.terrainaddon.client.AxiomIntegration;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.type.ImInt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Live-editable parameters for the terrain brush. Each control is backed by an ImGui-friendly
 * holder (single-element array or {@link ImInt}) so the widgets write straight into our state.
 * {@link #render()} draws Axiom's options panel and reports whether anything changed this frame,
 * so the tool can invalidate its ghost preview.
 *
 * <p>The target block comes from Axiom's own "Active Block" picker (via
 * {@link ToolService#getActiveBlock()}): whatever block the user has selected there is what the
 * terrain is filled with. The {@link #block} combo only kicks in as a fallback while Axiom reports
 * no selection.</p>
 */
public final class TerrainSettings {

    /** Index into {@link NoiseType#values()} / {@link NoiseType#DROPDOWN_NAMES}. */
    public final ImInt noiseType = new ImInt(0);

    /** Terrain amplitude AND vertical span of the region (WorldEdit "//terrain ... height"). */
    public final int[] height = {32};

    /** Noise frequency divisor: world coordinate divided by this before sampling. */
    public final float[] scale = {12.0f};

    /** Number of fBm octaves. */
    public final int[] octaves = {4};

    /** Per-octave amplitude falloff (fBm "gain"); the WorldEdit "roughness" argument. */
    public final float[] roughness = {0.5f};

    /** Region footprint in blocks (X extent), centred on the anchor. */
    public final int[] width = {48};

    /** Region footprint in blocks (Z extent), centred on the anchor. */
    public final int[] depth = {48};

    /** Fallback target fill block, used only while Axiom's "Active Block" is unset. */
    public final ImInt block = new ImInt(BlockChoices.indexOf(Blocks.STONE.defaultBlockState()));

    /** Last Active Block we saw; keeps filling with a briefly-removed selection instead of resetting. */
    private BlockState lastActiveBlock = null;

    /** Renders all controls; true if any value changed this frame. */
    public boolean render() {
        boolean changed = false;

        changed |= ImGui.combo("Noise Type", noiseType, NoiseType.DROPDOWN_NAMES);
        changed |= ImGui.sliderInt("Height", height, 1, 256);
        changed |= ImGui.sliderFloat("Scale", scale, 2.0f, 250.0f);
        changed |= ImGui.sliderInt("Octaves", octaves, 1, 8);
        changed |= ImGui.sliderFloat("Roughness", roughness, 0.0f, 1.0f);

        ImGui.separator();
        changed |= ImGui.sliderInt("Region Width", width, 1, 200);
        changed |= ImGui.sliderInt("Region Depth", depth, 1, 200);

        ImGui.separator();
        ToolService toolService = AxiomIntegration.toolService();
        BlockState active = toolService == null ? null : toolService.getActiveBlock();
        if (active != null) {
            // Show Axiom's currently-selected block; it is what the terrain will be filled with.
            ImGui.text("Target Block: " + active.getBlock().getName().getString());
            ImGui.textDisabled(BuiltInRegistries.BLOCK.getKey(active.getBlock()).toString());
        } else {
            // Axiom has no Active Block (or the internal hook is unavailable) - manual picker.
            changed |= BlockChoices.combo("Target Block", block);
        }

        return changed;
    }

    public NoiseType noiseType() {
        NoiseType[] values = NoiseType.values();
        int i = noiseType.get();
        return values[Math.max(0, Math.min(i, values.length - 1))];
    }

    /** The block the terrain is filled with: Axiom's Active Block, else the manual fallback. */
    public BlockState block() {
        ToolService toolService = AxiomIntegration.toolService();
        BlockState active = toolService == null ? null : toolService.getActiveBlock();
        if (active != null) {
            lastActiveBlock = active;
            return active;
        }
        if (lastActiveBlock != null) {
            return lastActiveBlock;
        }
        return BlockChoices.stateFor(block.get());
    }
}