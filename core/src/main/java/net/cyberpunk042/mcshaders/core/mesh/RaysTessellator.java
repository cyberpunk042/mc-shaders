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

import net.cyberpunk042.mcshaders.core.mesh.ray.RayContext;
import net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner;
import net.cyberpunk042.mcshaders.core.mesh.ray.RayTypeTessellator;
import net.cyberpunk042.mcshaders.core.mesh.ray.RayTypeTessellatorRegistry;
import net.cyberpunk042.mcshaders.core.diag.Diag;
import net.cyberpunk042.mcshaders.core.animation.WaveConfig;
import net.cyberpunk042.mcshaders.core.shape.RayType;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;

import java.util.Random;

/**
 * Tessellates rays shapes into meshes.
 * 
 * <h2>Architecture</h2>
 * <p>This class is the main orchestrator that:
 * <ul>
 *   <li>Loops through all rays (count × layers)</li>
 *   <li>Computes position/context via {@link RayPositioner}</li>
 *   <li>Delegates geometry generation to {@link RayTypeTessellator} implementations</li>
 * </ul>
 * 
 * <h2>Geometry</h2>
 * <p>Rays are line segments positioned according to the arrangement mode:</p>
 * <ul>
 *   <li>RADIAL - 2D star pattern on XZ plane, layers stack vertically</li>
 *   <li>SPHERICAL - 3D uniform distribution, layers create concentric shells</li>
 *   <li>PARALLEL - Grid of parallel rays pointing along +Y</li>
 *   <li>CONVERGING - Rays pointing toward center, layers create concentric shells</li>
 *   <li>DIVERGING - Rays pointing away from center, layers create concentric shells</li>
 * </ul>
 * 
 * <h2>Distribution Modes</h2>
 * <ul>
 *   <li><b>UNIFORM</b> - All rays have identical length and position within their pattern</li>
 *   <li><b>RANDOM</b> - Each ray has random start offset and random length within the radius range</li>
 *   <li><b>STOCHASTIC</b> - Heavily randomized with variable density and length</li>
 * </ul>
 * 
 * @see RaysShape
 * @see RayPositioner
 * @see RayTypeTessellator
 */
public final class RaysTessellator {
    
    private RaysTessellator() {}
    
    /**
     * Tessellates rays shape into a mesh (without wave deformation).
     * 
     * @param shape The rays shape definition
     * @return Mesh containing the ray geometry
     */
    public static Mesh tessellate(RaysShape shape) {
        return tessellate(shape, null, null, 0);
    }
    
    /**
     * Tessellates rays shape into a mesh with optional wave deformation.
     * 
     * @param shape The rays shape definition
     * @param wave Wave configuration (or null for no wave)
     * @param time Current time for wave animation
     * @return Mesh containing the ray geometry
     */
    public static Mesh tessellate(RaysShape shape, WaveConfig wave, float time) {
        return tessellate(shape, null, wave, time);
    }
    
    /**
     * Tessellates rays shape into a mesh with pattern and optional wave deformation.
     * 
     * @param shape The rays shape definition
     * @param pattern Pattern for tessellation (or null for filled)
     * @param wave Wave configuration (or null for no wave)
     * @param time Current time for wave animation
     * @return Mesh containing the ray geometry
     */
    public static Mesh tessellate(RaysShape shape, net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern, 
                                   WaveConfig wave, float time) {
        return tessellate(shape, pattern, null, wave, time, null);
    }
    
    /**
     * Tessellates rays shape with full pattern, visibility, and wave support.
     */
    public static Mesh tessellate(RaysShape shape, net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern, 
                                   net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility,
                                   WaveConfig wave, float time) {
        return tessellate(shape, pattern, visibility, wave, time, null);
    }
    
    /**
     * Tessellates rays shape with full pattern, visibility, wave, and flow animation support.
     * 
     * <p>This is the main entry point for 3D ray tessellation with animations.
     * The flow config enables RADIATE/ABSORB/CHASE position animations and
     * SCALE/CLIP edge transitions for 3D ray types.</p>
     * 
     * @param shape The rays shape definition
     * @param pattern Pattern for tessellation (or null for filled)
     * @param visibility Visibility mask (or null for all visible)
     * @param wave Wave configuration (or null for no wave)
     * @param time Current time for animation
     * @param flowConfig Flow animation config (or null for no flow)
     * @return Mesh containing the ray geometry
     */
    public static Mesh tessellate(RaysShape shape, net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern, 
                                   net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility,
                                   WaveConfig wave, float time,
                                   net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flowConfig) {
        RayType rayType = shape.effectiveRayType();
        
        // Get the tessellator for this ray type
        RayTypeTessellator tessellator = RayTypeTessellatorRegistry.get(rayType);
        
        // Choose mesh type based on BOTH ray type AND tessellator capability
        // If using RayLineTessellator (fallback for unimplemented types), must use LINES
        // Only use TRIANGLES if the type is 3D AND has a proper 3D tessellator
        boolean is3DWithProperTessellator = rayType.is3D() && 
            RayTypeTessellatorRegistry.isImplemented(rayType);
        
        MeshBuilder builder = is3DWithProperTessellator ? MeshBuilder.triangles() : MeshBuilder.lines();
        
        int count = shape.count();
        int layers = Math.max(1, shape.layers());
        
        Random rng = new Random(42); // Deterministic random for stable results
        
        Diag.FIELD.topic("tessellation").debug(
            "Tessellating rays: count={}, layers={}, arrangement={}, rayType={}, tessellator={}, meshType={}, pattern={}, hasFlow={}", 
            count, layers, shape.arrangement(), rayType, tessellator.name(),
            is3DWithProperTessellator ? "TRIANGLES" : "LINES",
            pattern != null ? pattern.getClass().getSimpleName() : "null",
            flowConfig != null && flowConfig.isActive());
        
        // Generate rays for each layer
        for (int layer = 0; layer < layers; layer++) {
            for (int i = 0; i < count; i++) {
                // Compute contexts for this ray (may return multiple for multi-copy mode)
                java.util.List<RayContext> contexts = RayPositioner.computeContexts(
                    shape, i, layer, rng, wave, time, flowConfig);
                
                // Tessellate each context (usually 1, but can be more for CONTINUOUS + waveCount > 1)
                for (RayContext context : contexts) {
                    tessellator.tessellate(builder, shape, context, pattern, visibility);
                }
            }
        }
        
        return builder.build();
    }
}

