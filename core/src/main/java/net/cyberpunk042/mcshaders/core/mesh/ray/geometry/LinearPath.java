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
 * Linear path implementation - simple straight line interpolation.
 */
public final class LinearPath implements GeoPath {
    
    private final float[] start;
    private final float[] end;
    private final float length;
    private final float[] direction;
    
    public LinearPath(float[] start, float[] end) {
        this.start = start.clone();
        this.end = end.clone();
        
        // Compute direction and length
        float dx = end[0] - start[0];
        float dy = end[1] - start[1];
        float dz = end[2] - start[2];
        this.length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (length > 0.0001f) {
            this.direction = new float[] { dx / length, dy / length, dz / length };
        } else {
            this.direction = new float[] { 0, 1, 0 };
        }
    }
    
    @Override
    public float[] positionAt(float t) {
        return new float[] {
            start[0] + (end[0] - start[0]) * t,
            start[1] + (end[1] - start[1]) * t,
            start[2] + (end[2] - start[2]) * t
        };
    }
    
    @Override
    public float[] tangentAt(float t) {
        return direction.clone();
    }
    
    @Override
    public float length() {
        return length;
    }
    
    @Override
    public float[] start() {
        return start.clone();
    }
    
    @Override
    public float[] end() {
        return end.clone();
    }
}
