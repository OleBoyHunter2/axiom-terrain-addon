package com.terrainaddon.client.terrain;

/**
 * Noise patterns supported by the terrain generator. This mirrors the original WorldEdit
 * addon's {@code NoiseType} exactly (PERLIN, SIMPLEX, RIDGE, FLAT, CELLULAR).
 */
public enum NoiseType {
    PERLIN,
    SIMPLEX,
    RIDGE,
    FLAT,
    CELLULAR;

    /** Display labels for the settings dropdown. The original enum calls it {@code RIDGE},
     *  kept here while the UI presents it as "RIDGED". Index order matches {@link #values()}. */
    public static final String[] DROPDOWN_NAMES = {
            "PERLIN", "SIMPLEX", "RIDGED", "FLAT", "CELLULAR"
    };

    public static NoiseType fromString(String s) {
        return switch (s.toLowerCase()) {
            case "perlin" -> PERLIN;
            case "simplex" -> SIMPLEX;
            case "ridge", "ridged" -> RIDGE;
            case "flat" -> FLAT;
            case "cellular" -> CELLULAR;
            default -> throw new IllegalArgumentException("Unknown noise type: " + s);
        };
    }

    @Override
    public String toString() {
        return DROPDOWN_NAMES[ordinal()];
    }
}