package com.terrainaddon.client.terrain;

import imgui.moulberry92.ImGui;
import imgui.moulberry92.type.ImInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A curated palette of blocks the terrain brush can fill with, plus a small ImGui combo
 * helper for picking one. The {@link TerrainTool} uses it to expose the "target block"
 * parameter from the WorldEdit command ({@code ... <block>}).
 */
public final class BlockChoices {

    public static final BlockState[] STATES;
    public static final String[] NAMES;

    static {
        List<BlockState> states = new ArrayList<>();
        List<String> names = new ArrayList<>();

        add(states, names, "Stone", Blocks.STONE);
        add(states, names, "Grass Block", Blocks.GRASS_BLOCK);
        add(states, names, "Dirt", Blocks.DIRT);
        add(states, names, "Coarse Dirt", Blocks.COARSE_DIRT);
        add(states, names, "Podzol", Blocks.PODZOL);
        add(states, names, "Sand", Blocks.SAND);
        add(states, names, "Red Sand", Blocks.RED_SAND);
        add(states, names, "Sandstone", Blocks.SANDSTONE);
        add(states, names, "Cobblestone", Blocks.COBBLESTONE);
        add(states, names, "Mossy Cobblestone", Blocks.MOSSY_COBBLESTONE);
        add(states, names, "Stone Bricks", Blocks.STONE_BRICKS);
        add(states, names, "Deepslate", Blocks.DEEPSLATE);
        add(states, names, "Andesite", Blocks.ANDESITE);
        add(states, names, "Diorite", Blocks.DIORITE);
        add(states, names, "Granite", Blocks.GRANITE);
        add(states, names, "Tuff", Blocks.TUFF);
        add(states, names, "Basalt", Blocks.BASALT);
        add(states, names, "Blackstone", Blocks.BLACKSTONE);
        add(states, names, "Snow Block", Blocks.SNOW_BLOCK);
        add(states, names, "Packed Ice", Blocks.PACKED_ICE);
        add(states, names, "Netherrack", Blocks.NETHERRACK);
        add(states, names, "Magma Block", Blocks.MAGMA_BLOCK);

        STATES = states.toArray(new BlockState[0]);
        NAMES = names.toArray(new String[0]);
    }

    private BlockChoices() {
    }

    private static void add(List<BlockState> states, List<String> names, String name, net.minecraft.world.level.block.Block block) {
        states.add(block.defaultBlockState());
        names.add(name);
    }

    /** Renders a block-picker combo bound to {@code index}; true if the selection changed. */
    public static boolean combo(String label, ImInt index) {
        return ImGui.combo(label, index, NAMES);
    }

    /** The state for the given palette index, clamped to a valid entry. */
    public static BlockState stateFor(int index) {
        if (index < 0 || index >= STATES.length) {
            return STATES[0];
        }
        return STATES[index];
    }

    /** Palette index of the given state, or 0 if it is not part of the curated list. */
    public static int indexOf(BlockState state) {
        for (int i = 0; i < STATES.length; i++) {
            if (STATES[i] == state || STATES[i].equals(state)) {
                return i;
            }
        }
        return 0;
    }
}