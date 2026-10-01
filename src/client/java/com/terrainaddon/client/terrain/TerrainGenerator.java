package com.terrainaddon.client.terrain;

/**
 * Layered / fractal noise used to displace a terrain heightmap, ported 1:1 from the original
 * WorldEdit terrain addon's {@code TerrainGenerator} (PERLIN / SIMPLEX / RIDGE / FLAT /
 * CELLULAR via octave-layered fBm).
 *
 * <p>This class is deliberately pure math - it holds no Minecraft or WorldEdit types - so it can
 * be reused unchanged from any edit pipeline (Axiom, WorldEdit, a dedicated datagen pass, ...).
 * The {@code normalizeHeight} method reproduces exactly what the original {@code generate(...)}
 * did per (x, z) column: sample the fBm heightfield, remap [-1,1] -&gt; [0,1], clamp, then scale
 * by the configured height.</p>
 *
 * <p>Every literal (the {@code 0.366025} simplex skew, the {@code 0.9} / {@code 1.1} warp, the
 * {@code 73244475} hash multiplier, the {@code 2.0} lacunarity and {@code 0.5} starting
 * amplitude) is preserved verbatim, so previews are bit-for-bit the same shape the WorldEdit
 * version produced for a given seed.</p>
 */
public final class TerrainGenerator {

    private long seed;

    public TerrainGenerator() {
        this(System.currentTimeMillis());
    }

    public TerrainGenerator(long seed) {
        this.seed = seed;
    }

    public long seed() {
        return seed;
    }

    /** Re-seeds the permutation so the noise field changes while parameters stay the same. */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    // ----------------------------------------------------------------------------------
    // Helpers (identical to the original)
    // ----------------------------------------------------------------------------------

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double grad(int hash, double x, double y) {
        int h = hash & 7;
        double u = h < 4 ? x : y;
        double v = h < 4 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** Seeded pseudo-random permutation lookup, ported verbatim from the original. */
    private int p(int i) {
        i ^= (int) (seed >> 16);
        i &= 255;
        i = (i ^ (i >> 4)) * 73244475;
        i = (i ^ (i >> 4)) * 73244475;
        i ^= (i >> 4);
        return i & 255;
    }

    // ----------------------------------------------------------------------------------
    // Base noise primitives
    // ----------------------------------------------------------------------------------

    public double perlin(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf);
        double v = fade(yf);

        int a = p(xi + p(yi));
        int b = p(xi + p(yi + 1));
        int c = p(xi + 1 + p(yi));
        int d = p(xi + 1 + p(yi + 1));

        double x1 = lerp(grad(a, xf, yf),     grad(c, xf - 1, yf),     u);
        double x2 = lerp(grad(b, xf, yf - 1), grad(d, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }

    /** The "simplex" style noise from the original (a skewed, re-scaled Perlin sample). */
    public double simplex(double x, double y) {
        double s = (x + y) * 0.366025;
        return perlin((x + s) * 0.9, (y + s) * 0.9) * 1.1;
    }

    /** Ridged noise: {@code 1 - |2 * perlin|}. */
    public double ridge(double x, double y) {
        return 1.0 - Math.abs(perlin(x, y) * 2.0);
    }

    /** Cellular (Worley-style) noise using the seeded permutation as feature points. */
    public double cellular(double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double minDist = Double.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                int hashBase = xi + ox + p(yi + oy);
                double px = (xi + ox) + p(hashBase) / 255.0;
                double py = (yi + oy) + p(hashBase + 7) / 255.0;
                double dx = x - px;
                double dy = y - py;
                double dist = Math.sqrt(dx * dx + dy * dy);
                minDist = Math.min(minDist, dist);
            }
        }
        return Math.min(minDist, 1.0) * 2.0 - 1.0;
    }

    private double getRaw(NoiseType type, double x, double y) {
        return switch (type) {
            case PERLIN -> perlin(x, y);
            case SIMPLEX -> simplex(x, y);
            case RIDGE -> ridge(x, y);
            case CELLULAR -> cellular(x, y);
            case FLAT -> 0.0;
        };
    }

    // ----------------------------------------------------------------------------------
    // Fractal Brownian motion + heightfield
    // ----------------------------------------------------------------------------------

    /**
     * Layered fractal noise. Each octave keeps the carrier frequency (lacunarity) and shrinks
     * its amplitude by {@code gain}; the original calls this with lacunarity fixed at 2.0 and
     * gain equal to the tool's "roughness".
     */
    public double fbm(NoiseType type, double x, double y, int octaves, double lacunarity, double gain) {
        double total = 0;
        double amplitude = 0.5;
        double frequency = 1.0;
        for (int i = 0; i < octaves; i++) {
            total += amplitude * getRaw(type, x * frequency, y * frequency);
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return total;
    }

    /**
     * Normalised column height for a single (x, z) sample, in {@code [0, 1]}.
     *
     * <p>FLAT returns a constant 0.5; any other type runs {@link #fbm} on
     * {@code (x/scale, z/scale)}, remaps the roughly-[-1,1] result to [-&gt;0..1] via
     * {@code (n+1)/2} and clamps. {@code octaves} is the number of noise octaves and
     * {@code roughness} is the per-octave amplitude falloff.</p>
     */
    public double normalizedHeight(int x, int z, NoiseType type, double scale, int octaves, double roughness) {
        if (type == NoiseType.FLAT) {
            return 0.5;
        }
        double n = fbm(type, x / scale, z / scale, octaves, 2.0, roughness);
        n = (n + 1.0) / 2.0;
        return Math.max(0.0, Math.min(1.0, n));
    }
}