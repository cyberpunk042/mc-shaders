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
package net.cyberpunk042.mcshaders.core.mesh.ray.geometry3d;

import net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode;

/**
 * Proper spaghettification deformation based on Ray3DGeometryUtils.generateDropletWithGravity.
 * 
 * <p>Applies per-vertex deformation based on tidal forces, following the physics formula:
 * <ol>
 *   <li>Compute direction from field center (black hole) to each vertex</li>
 *   <li>Compute tidal influence factor: K / distance³</li>
 *   <li>Separate vertex offset into radial and lateral components</li>
 *   <li>Stretch radial, compress lateral: displacement = radial*tidal - lateral*tidal</li>
 * </ol>
 *
 * <p>This creates authentic "spaghettification" - stretching toward center, thinning perpendicular.
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.Ray3DGeometryUtils#generateDropletWithGravity
 */
public final class GeoSpaghettification implements GeoDeformationStrategy {
    
    private final FieldDeformationMode mode;
    
    public static final GeoSpaghettification GRAVITATIONAL = new GeoSpaghettification(FieldDeformationMode.GRAVITATIONAL);
    public static final GeoSpaghettification REPULSION = new GeoSpaghettification(FieldDeformationMode.REPULSION);
    public static final GeoSpaghettification TIDAL = new GeoSpaghettification(FieldDeformationMode.TIDAL);
    
    /** Default instance - GRAVITATIONAL mode. */
    public static final GeoSpaghettification INSTANCE = GRAVITATIONAL;
    
    public GeoSpaghettification(FieldDeformationMode mode) {
        this.mode = mode != null ? mode : FieldDeformationMode.GRAVITATIONAL;
    }
    
    @Override
    public void apply(float[] position, float t, float[] fieldCenter, float intensity, float fieldRadius) {
        if (intensity < 0.001f || fieldRadius < 0.001f) {
            return;
        }
        
        // From Ray3DGeometryUtils.generateDropletWithGravity lines 432-498
        
        // 1. Compute vector FROM field center TO this vertex
        float vfX = position[0] - fieldCenter[0];
        float vfY = position[1] - fieldCenter[1];
        float vfZ = position[2] - fieldCenter[2];
        float vfDist = (float) Math.sqrt(vfX * vfX + vfY * vfY + vfZ * vfZ);
        
        // Minimum distance to prevent division by zero
        float minDist = fieldRadius * 0.05f;
        float effectiveDist = Math.max(vfDist, minDist);
        
        // 2. Direction from field center to vertex (normalized)
        float dirX = vfX / effectiveDist;
        float dirY = vfY / effectiveDist;
        float dirZ = vfZ / effectiveDist;
        
        // 3. Compute tidal influence factor: K / dist³
        //    Scale K so that the effect is reasonable at the field's outer radius
        float kTidal = intensity * fieldRadius * fieldRadius * fieldRadius;
        float tidalFactor = kTidal / (effectiveDist * effectiveDist * effectiveDist);
        tidalFactor = Math.min(tidalFactor, 2.0f);  // Cap to prevent extreme distortion
        
        // 4. The position is relative to droplet center (assumed to be at origin for this call)
        //    For a more accurate implementation, we'd need the droplet center and direction
        //    For now, use the field center direction as the radial direction
        float radialMag = position[0] * dirX + position[1] * dirY + position[2] * dirZ;
        float radialX = radialMag * dirX;
        float radialY = radialMag * dirY;
        float radialZ = radialMag * dirZ;
        
        // Lateral = perpendicular component
        float lateralX = position[0] - radialX;
        float lateralY = position[1] - radialY;
        float lateralZ = position[2] - radialZ;
        
        // 5. Apply spaghettification: stretch radial, compress lateral
        float dispX, dispY, dispZ;
        
        switch (mode) {
            case GRAVITATIONAL -> {
                // Stretch toward center (radial), compress lateral
                dispX = radialX * tidalFactor - lateralX * tidalFactor * 0.5f;
                dispY = radialY * tidalFactor - lateralY * tidalFactor * 0.5f;
                dispZ = radialZ * tidalFactor - lateralZ * tidalFactor * 0.5f;
            }
            case REPULSION -> {
                // Stretch away from center (invert radial effect)
                dispX = -radialX * tidalFactor - lateralX * tidalFactor * 0.5f;
                dispY = -radialY * tidalFactor - lateralY * tidalFactor * 0.5f;
                dispZ = -radialZ * tidalFactor - lateralZ * tidalFactor * 0.5f;
            }
            case TIDAL -> {
                // Pure tidal: both stretch and compress more equally
                dispX = radialX * tidalFactor * 0.8f - lateralX * tidalFactor * 0.8f;
                dispY = radialY * tidalFactor * 0.8f - lateralY * tidalFactor * 0.8f;
                dispZ = radialZ * tidalFactor * 0.8f - lateralZ * tidalFactor * 0.8f;
            }
            default -> {
                dispX = 0; dispY = 0; dispZ = 0;
            }
        }
        
        // 6. Apply displacement
        position[0] += dispX;
        position[1] += dispY;
        position[2] += dispZ;
    }
}
