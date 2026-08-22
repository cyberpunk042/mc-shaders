/*
 * Ported from the-virus-block-mc (net.cyberpunk042.client.visual.mesh), where it
 * is tessellation code with no Minecraft or graphics-API dependency: the whole
 * subtree imported nothing from net.minecraft or com.mojang.
 * Relicensed to MIT here by the author, per the engine/content split recorded in
 * docs/PORTING.md.
 *
 * The mod's logging system was replaced with core's Diag, which routes to
 * java.lang.System.Logger. The call sites are unchanged apart from the name.
 */
package net.cyberpunk042.mcshaders.core.mesh.ray.geometry;

/**
 * Gravitational curvature - rays bend toward/away from center.
 * Creates a black hole or repulsion field effect.
 */
public final class GravitationalCurvature implements CurvatureStrategy {
    
    private final boolean attraction;  // true = toward center, false = away
    
    public static final GravitationalCurvature ATTRACT = new GravitationalCurvature(true);
    public static final GravitationalCurvature REPEL = new GravitationalCurvature(false);
    
    private GravitationalCurvature(boolean attraction) {
        this.attraction = attraction;
    }
    
    @Override
    public float[] apply(float[] position, float t, float intensity, float[] center) {
        if (intensity < 0.001f) {
            return position;
        }
        
        // Compute direction from position to center
        float dx = center[0] - position[0];
        float dy = center[1] - position[1];
        float dz = center[2] - position[2];
        
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.001f) {
            return position;
        }
        
        // Normalize direction
        dx /= dist;
        dy /= dist;
        dz /= dist;
        
        // Gravitational effect: stronger in the middle of the ray
        float effect = 4f * t * (1 - t);  // Peaks at t=0.5
        float pull = intensity * effect * 0.5f;
        
        if (!attraction) {
            pull = -pull;  // Repel instead of attract
        }
        
        position[0] += dx * pull;
        position[1] += dy * pull;
        position[2] += dz * pull;
        
        return position;
    }
}
