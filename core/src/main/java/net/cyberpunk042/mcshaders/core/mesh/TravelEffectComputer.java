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
package net.cyberpunk042.mcshaders.core.mesh;

import net.cyberpunk042.mcshaders.core.mesh.ray.flow.FlowTravelStage;
import net.cyberpunk042.mcshaders.core.animation.Axis;
import net.cyberpunk042.mcshaders.core.animation.TravelDirection;
import net.cyberpunk042.mcshaders.core.animation.TravelEffectConfig;
import net.cyberpunk042.mcshaders.core.energy.EnergyTravel;
import net.cyberpunk042.mcshaders.core.energy.TravelBlendMode;

/**
 * Utility for computing travel effects on any shape.
 * 
 * <p>This class provides methods to compute the 't' position (0-1 along travel axis)
 * for any 3D vertex, then applies the travel effect to compute the final alpha.</p>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // In a tessellator, for each vertex:
 * float t = TravelEffectComputer.computeT(vertexX, vertexY, vertexZ, config, bounds);
 * float alpha = TravelEffectComputer.computeAlpha(baseAlpha, t, config, phase);
 * }</pre>
 * 
 * @see TravelEffectConfig
 * @see FlowTravelStage
 */
public final class TravelEffectComputer {
    
    private TravelEffectComputer() {}  // Static utility class
    
    /**
     * Compute 't' position (0-1) for a vertex based on its position along the travel axis.
     * 
     * <p>The vertex position is projected onto the travel direction axis, then
     * normalized to 0-1 based on the shape's bounding box along that axis.</p>
     * 
     * @param vx Vertex X position (local to shape center)
     * @param vy Vertex Y position (local to shape center)
     * @param vz Vertex Z position (local to shape center)
     * @param config Travel effect configuration
     * @param minBound Minimum extent in travel direction
     * @param maxBound Maximum extent in travel direction
     * @return Position along travel axis (0 = start, 1 = end)
     */
    public static float computeT(float vx, float vy, float vz, 
            TravelEffectConfig config, float minBound, float maxBound) {
        
        float[] dir = config.getDirectionVector();
        
        // Project vertex onto direction axis (dot product)
        float projection = vx * dir[0] + vy * dir[1] + vz * dir[2];
        
        // Normalize to 0-1 range based on bounds
        float range = maxBound - minBound;
        if (range < 0.001f) return 0.5f;  // Degenerate case
        
        return (projection - minBound) / range;
    }
    
    /**
     * Compute 't' position for a vertex using axis-aligned bounds.
     * 
     * @param vx Vertex X (local)
     * @param vy Vertex Y (local)
     * @param vz Vertex Z (local)
     * @param config Travel effect configuration
     * @param boundsMin Array of [minX, minY, minZ]
     * @param boundsMax Array of [maxX, maxY, maxZ]
     * @return Position along travel axis (0 = start, 1 = end)
     */
    public static float computeT(float vx, float vy, float vz,
            TravelEffectConfig config, float[] boundsMin, float[] boundsMax) {
        
        TravelDirection travelDir = config.effectiveTravelDirection();
        
        // Handle non-linear travel directions
        switch (travelDir) {
            case RADIAL -> {
                // Distance from center in XZ plane, normalized to max extent
                float distXZ = (float) Math.sqrt(vx * vx + vz * vz);
                // Use the larger of X or Z extent as the outer radius
                float maxRadius = Math.max(boundsMax[0], boundsMax[2]);
                if (maxRadius < 0.001f) return 0.5f;
                return Math.min(1f, distXZ / maxRadius);
            }
            case ANGULAR -> {
                // Angle around Y axis, mapped from [0, 2π] to [0, 1]
                float angle = (float) Math.atan2(vz, vx);  // -π to π
                // Normalize to 0-1
                return (angle + (float) Math.PI) / (2f * (float) Math.PI);
            }
            case SPHERICAL -> {
                // Distance from origin, normalized to max extent
                float dist = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                float maxDist = Math.max(Math.max(boundsMax[0], boundsMax[1]), boundsMax[2]);
                if (maxDist < 0.001f) return 0.5f;
                return Math.min(1f, dist / maxDist);
            }
            case LINEAR -> {
                // Fall through to linear handling below
            }
        }
        
        // LINEAR mode: use axis-aligned projection
        Axis axis = config.effectiveDirection();
        if (axis == Axis.CUSTOM) {
            // Custom direction: compute projection bounds
            float[] dir = config.getDirectionVector();
            float minProj = boundsMin[0] * dir[0] + boundsMin[1] * dir[1] + boundsMin[2] * dir[2];
            float maxProj = boundsMax[0] * dir[0] + boundsMax[1] * dir[1] + boundsMax[2] * dir[2];
            return computeT(vx, vy, vz, config, Math.min(minProj, maxProj), Math.max(minProj, maxProj));
        }
        
        // Simple axis-aligned case
        return switch (axis) {
            case X -> computeT(vx, vy, vz, config, boundsMin[0], boundsMax[0]);
            case Y -> computeT(vx, vy, vz, config, boundsMin[1], boundsMax[1]);
            case Z -> computeT(vx, vy, vz, config, boundsMin[2], boundsMax[2]);
            default -> computeT(vx, vy, vz, config, boundsMin[1], boundsMax[1]);  // Default to Y
        };
    }
    
    /**
     * Compute 't' for a sphere vertex based on its normalized position.
     * 
     * <p>For spheres, we use the direction component of the normal
     * since all vertices are on the unit sphere surface.</p>
     * 
     * @param nx Normal X (also vertex direction for unit sphere)
     * @param ny Normal Y
     * @param nz Normal Z
     * @param config Travel effect configuration
     * @return Position along travel axis (0 = start, 1 = end)
     */
    public static float computeTForSphere(float nx, float ny, float nz, TravelEffectConfig config) {
        float[] dir = config.getDirectionVector();
        
        // Dot product gives -1 to +1 along direction
        float dot = nx * dir[0] + ny * dir[1] + nz * dir[2];
        
        // Remap from [-1, 1] to [0, 1]
        return (dot + 1f) * 0.5f;
    }
    
    /**
     * Compute final alpha for a vertex with travel effect applied.
     * 
     * @param baseAlpha Original vertex alpha before travel
     * @param t Position along travel axis (0-1)
     * @param config Travel effect configuration
     * @param phase Animation phase (0-1, varies over time)
     * @return Final alpha with travel effect applied
     */
    public static float computeAlpha(float baseAlpha, float t, TravelEffectConfig config, float phase) {
        if (config == null || !config.isActive()) {
            return baseAlpha;
        }
        
        // Use the existing ray travel stage computation
        return FlowTravelStage.computeBlendedAlpha(
            baseAlpha,
            t,
            config.effectiveMode(),
            phase,
            config.count(),
            config.width(),
            config.effectiveBlendMode(),
            config.intensity(),
            config.minAlpha()
        );
    }
    
    /**
     * Convenience method: compute alpha from vertex position for sphere shapes.
     * 
     * @param nx Normal/direction X
     * @param ny Normal/direction Y  
     * @param nz Normal/direction Z
     * @param baseAlpha Original alpha
     * @param config Travel effect configuration
     * @param phase Animation phase (0-1)
     * @return Final alpha with travel effect
     */
    public static float computeSphereAlpha(float nx, float ny, float nz,
            float baseAlpha, TravelEffectConfig config, float phase) {
        
        if (config == null || !config.isActive()) {
            return baseAlpha;
        }
        
        float t = computeTForSphere(nx, ny, nz, config);
        return computeAlpha(baseAlpha, t, config, phase);
    }
}
