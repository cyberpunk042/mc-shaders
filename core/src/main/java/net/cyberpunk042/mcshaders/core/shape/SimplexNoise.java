/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 */
package net.cyberpunk042.mcshaders.core.shape;

/**
 * 3D Simplex Noise implementation for procedural terrain generation.
 * 
 * <p>Based on Stefan Gustavson's "Simplex Noise Demystified" paper and
 * Ken Perlin's improved noise algorithm. This implementation is optimized
 * for spherical terrain sampling.</p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li>Pure Java, no external dependencies</li>
 *   <li>3D noise for seamless spherical sampling</li>
 *   <li>Fractal Brownian Motion (fBm) for layered detail</li>
 *   <li>Ridged Multifractal for mountain peaks</li>
 *   <li>Seeded for reproducible results</li>
 * </ul>
 * 
 * @see SphereDeformation#PLANET
 */
public final class SimplexNoise {
    
    private SimplexNoise() {} // Utility class
    
    // =========================================================================
    // CONSTANTS
    // =========================================================================
    
    /**
     * Gradient vectors for 3D simplex noise.
     * These are the 12 midpoints of the edges of a cube centered on origin.
     */
    private static final int[][] GRAD3 = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };
    
    /**
     * Permutation table (256 entries, duplicated for overflow avoidance).
     * This is the standard Perlin permutation table.
     */
    private static final int[] PERM = new int[512];
    private static final int[] PERM_MOD12 = new int[512];
    
    static {
        // Standard permutation table
        int[] p = {
            151, 160, 137, 91, 90, 15, 131, 13, 201, 95, 96, 53, 194, 233, 7, 225,
            140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23, 190, 6, 148,
            247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32,
            57, 177, 33, 88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175,
            74, 165, 71, 134, 139, 48, 27, 166, 77, 146, 158, 231, 83, 111, 229, 122,
            60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244, 102, 143, 54,
            65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169,
            200, 196, 135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64,
            52, 217, 226, 250, 124, 123, 5, 202, 38, 147, 118, 126, 255, 82, 85, 212,
            207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42, 223, 183, 170, 213,
            119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9,
            129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104,
            218, 246, 97, 228, 251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241,
            81, 51, 145, 235, 249, 14, 239, 107, 49, 192, 214, 31, 181, 199, 106, 157,
            184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254, 138, 236, 205, 93,
            222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180
        };
        
        for (int i = 0; i < 256; i++) {
            PERM[i] = p[i];
            PERM[256 + i] = p[i];
            PERM_MOD12[i] = p[i] % 12;
            PERM_MOD12[256 + i] = p[i] % 12;
        }
    }
    
    // Skewing factors for 3D simplex
    private static final float F3 = 1.0f / 3.0f;
    private static final float G3 = 1.0f / 6.0f;
    
    // =========================================================================
    // CORE 3D SIMPLEX NOISE
    // =========================================================================
    
    /**
     * Computes 3D simplex noise at the given coordinates.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Noise value in range [-1, 1]
     */
    public static float noise3D(float x, float y, float z) {
        // Skew the input space to determine which simplex cell we're in
        float s = (x + y + z) * F3;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        int k = fastFloor(z + s);
        
        // Unskew the cell origin back to (x, y, z) space
        float t = (i + j + k) * G3;
        float X0 = i - t;
        float Y0 = j - t;
        float Z0 = k - t;
        
        // The x, y, z distances from the cell origin
        float x0 = x - X0;
        float y0 = y - Y0;
        float z0 = z - Z0;
        
        // Determine which simplex we are in (there are 6 possibilities)
        int i1, j1, k1; // Offsets for second corner of simplex in (i,j,k) coords
        int i2, j2, k2; // Offsets for third corner of simplex in (i,j,k) coords
        
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; // X Y Z order
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; // X Z Y order
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; // Z X Y order
            }
        } else { // x0 < y0
            if (y0 < z0) {
                i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; // Z Y X order
            } else if (x0 < z0) {
                i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; // Y Z X order
            } else {
                i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; // Y X Z order
            }
        }
        
        // Offsets for remaining corners
        float x1 = x0 - i1 + G3;
        float y1 = y0 - j1 + G3;
        float z1 = z0 - k1 + G3;
        float x2 = x0 - i2 + 2.0f * G3;
        float y2 = y0 - j2 + 2.0f * G3;
        float z2 = z0 - k2 + 2.0f * G3;
        float x3 = x0 - 1.0f + 3.0f * G3;
        float y3 = y0 - 1.0f + 3.0f * G3;
        float z3 = z0 - 1.0f + 3.0f * G3;
        
        // Work out the hashed gradient indices of the four simplex corners
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        int gi0 = PERM_MOD12[ii + PERM[jj + PERM[kk]]];
        int gi1 = PERM_MOD12[ii + i1 + PERM[jj + j1 + PERM[kk + k1]]];
        int gi2 = PERM_MOD12[ii + i2 + PERM[jj + j2 + PERM[kk + k2]]];
        int gi3 = PERM_MOD12[ii + 1 + PERM[jj + 1 + PERM[kk + 1]]];
        
        // Calculate the contribution from the four corners
        float n0, n1, n2, n3;
        
        float t0 = 0.6f - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 < 0) {
            n0 = 0.0f;
        } else {
            t0 *= t0;
            n0 = t0 * t0 * dot(GRAD3[gi0], x0, y0, z0);
        }
        
        float t1 = 0.6f - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 < 0) {
            n1 = 0.0f;
        } else {
            t1 *= t1;
            n1 = t1 * t1 * dot(GRAD3[gi1], x1, y1, z1);
        }
        
        float t2 = 0.6f - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 < 0) {
            n2 = 0.0f;
        } else {
            t2 *= t2;
            n2 = t2 * t2 * dot(GRAD3[gi2], x2, y2, z2);
        }
        
        float t3 = 0.6f - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 < 0) {
            n3 = 0.0f;
        } else {
            t3 *= t3;
            n3 = t3 * t3 * dot(GRAD3[gi3], x3, y3, z3);
        }
        
        // Scale to [-1, 1]
        return 32.0f * (n0 + n1 + n2 + n3);
    }
    
    /**
     * Seeded 3D simplex noise.
     * Adds a deterministic offset based on seed for variation.
     */
    public static float noise3D(float x, float y, float z, int seed) {
        // Use seed to offset coordinates by a large prime-based amount
        float seedOffset = seed * 0.123456f;
        return noise3D(x + seedOffset, y + seedOffset * 1.7f, z + seedOffset * 2.3f);
    }
    
    // =========================================================================
    // FRACTAL BROWNIAN MOTION (fBm)
    // =========================================================================
    
    /**
     * Fractal Brownian Motion - layered noise for natural terrain.
     * 
     * <p>Each octave adds finer detail with decreasing amplitude.</p>
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param octaves Number of noise layers (1-8)
     * @param lacunarity Frequency multiplier per octave (typically 2.0)
     * @param persistence Amplitude multiplier per octave (typically 0.5)
     * @param seed Random seed
     * @return Noise value in range approximately [-1, 1]
     */
    public static float fBm(float x, float y, float z, 
            int octaves, float lacunarity, float persistence, int seed) {
        
        float total = 0;
        float amplitude = 1;
        float frequency = 1;
        float maxValue = 0;
        
        for (int i = 0; i < octaves; i++) {
            total += amplitude * noise3D(x * frequency, y * frequency, z * frequency, seed);
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        
        return total / maxValue;  // Normalize to approximately [-1, 1]
    }
    
    /**
     * fBm with default lacunarity (2.0) and persistence (0.5).
     */
    public static float fBm(float x, float y, float z, int octaves, int seed) {
        return fBm(x, y, z, octaves, 2.0f, 0.5f, seed);
    }
    
    // =========================================================================
    // RIDGED MULTIFRACTAL NOISE
    // =========================================================================
    
    /**
     * Ridged Multifractal noise for sharp mountain peaks.
     * 
     * <p>Key insight: Uses |noise| to create V-shaped ridges, then inverts
     * to make ridges point upward. The feedback loop makes rough areas rougher.</p>
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param octaves Number of noise layers (1-8)
     * @param lacunarity Frequency multiplier per octave (typically 2.0)
     * @param gain Feedback strength (1.0-3.0), higher = more dramatic ridges
     * @param offset Added to signal (0.5-1.5), higher = rougher terrain
     * @param seed Random seed
     * @return Noise value in range [0, ~2]
     */
    public static float ridgedMultifractal(float x, float y, float z,
            int octaves, float lacunarity, float gain, float offset, int seed) {
        
        float sum = 0;
        float amplitude = 0.5f;
        float frequency = 1;
        float weight = 1;
        
        for (int i = 0; i < octaves; i++) {
            // Get base noise
            float signal = noise3D(x * frequency, y * frequency, z * frequency, seed);
            
            // Create ridge: invert absolute value
            signal = offset - Math.abs(signal);
            
            // Sharpen the ridges
            signal = signal * signal;
            
            // Weight based on previous octave (feedback loop)
            signal *= weight;
            
            // Update weight for next octave
            weight = clamp(signal * gain, 0, 1);
            
            sum += signal * amplitude;
            amplitude *= 0.5f;  // Fixed persistence for ridged noise
            frequency *= lacunarity;
        }
        
        return sum;
    }
    
    /**
     * Ridged multifractal with default parameters.
     */
    public static float ridgedMultifractal(float x, float y, float z, int octaves, int seed) {
        return ridgedMultifractal(x, y, z, octaves, 2.0f, 2.0f, 1.0f, seed);
    }
    
    // =========================================================================
    // BLENDING FUNCTIONS
    // =========================================================================
    
    /**
     * Blends between fBm and ridged multifractal noise.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param octaves Number of noise layers
     * @param lacunarity Frequency multiplier
     * @param persistence Amplitude decay for fBm
     * @param ridgedAmount Blend (0 = pure fBm, 1 = pure ridged)
     * @param seed Random seed
     * @return Blended noise value
     */
    public static float terrainNoise(float x, float y, float z,
            int octaves, float lacunarity, float persistence,
            float ridgedAmount, int seed) {
        
        if (ridgedAmount <= 0.01f) {
            return fBm(x, y, z, octaves, lacunarity, persistence, seed);
        }
        
        if (ridgedAmount >= 0.99f) {
            // Scale ridged to similar range as fBm
            return ridgedMultifractal(x, y, z, octaves, lacunarity, 2.0f, 1.0f, seed) - 0.5f;
        }
        
        // Blend between the two
        float fBmValue = fBm(x, y, z, octaves, lacunarity, persistence, seed);
        float ridgedValue = ridgedMultifractal(x, y, z, octaves, lacunarity, 2.0f, 1.0f, seed) - 0.5f;
        
        return lerp(fBmValue, ridgedValue, ridgedAmount);
    }
    
    // =========================================================================
    // UTILITY FUNCTIONS
    // =========================================================================
    
    private static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
    
    private static float dot(int[] g, float x, float y, float z) {
        return g[0] * x + g[1] * y + g[2] * z;
    }
    
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }
}
