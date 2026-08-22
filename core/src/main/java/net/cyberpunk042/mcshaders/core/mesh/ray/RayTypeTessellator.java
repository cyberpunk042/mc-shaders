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
package net.cyberpunk042.mcshaders.core.mesh.ray;

import net.cyberpunk042.mcshaders.core.mesh.MeshBuilder;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;

/**
 * Interface for ray type-specific tessellation.
 * 
 * <p>Each implementation handles the geometry generation for a specific
 * {@link net.cyberpunk042.mcshaders.core.shape.RayType}. The common position/arrangement
 * logic is handled by {@link RayPositioner}, and implementations receive
 * a {@link RayContext} with all computed position data.
 * 
 * <h2>Implementation Guidelines</h2>
 * <ul>
 *   <li>Use {@link RayContext#start()}, {@link RayContext#end()}, etc. for positioning</li>
 *   <li>Respect {@link RayContext#fadeStart()} and {@link RayContext#fadeEnd()} for alpha</li>
 *   <li>Use {@link RayContext#width()} for thickness calculations</li>
 *   <li>For complex shapes, use {@link RayContext#lineResolution()} for subdivision</li>
 * </ul>
 * 
 * <h2>Mesh Building</h2>
 * <p>Use the {@link MeshBuilder} to emit geometry:
 * <ul>
 *   <li>For LINE type: use {@link MeshBuilder#line(int, int)}</li>
 *   <li>For 3D types: use {@link MeshBuilder#triangle(int, int, int)} or quad patterns</li>
 * </ul>
 * 
 * @see RayContext
 * @see RayPositioner
 * @see net.cyberpunk042.mcshaders.core.shape.RayType
 */
public interface RayTypeTessellator {
    
    /**
     * Tessellates a single ray into the mesh builder.
     * 
     * @param builder The mesh builder to emit vertices and primitives to
     * @param shape The parent rays shape configuration
     * @param context The computed context for this specific ray (position, direction, etc.)
     */
    void tessellate(MeshBuilder builder, RaysShape shape, RayContext context);
    
    /**
     * Tessellates a single ray with pattern and visibility support.
     * 
     * @param builder The mesh builder to emit vertices and primitives to
     * @param shape The parent rays shape configuration
     * @param context The computed context for this specific ray
     * @param pattern Pattern for cell rendering (or null for filled)
     * @param visibility Visibility mask (or null for full visibility)
     */
    default void tessellate(MeshBuilder builder, RaysShape shape, RayContext context,
                            net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern,
                            net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility) {
        // Default: ignore pattern and visibility
        tessellate(builder, shape, context);
    }
    
    /**
     * Returns the name of this tessellator for debugging/logging.
     */
    default String name() {
        return getClass().getSimpleName();
    }
    
    /**
     * Whether this tessellator requires per-frame re-tessellation.
     * <p>Procedural types (lightning, plasma) may return true.
     */
    default boolean isProcedural() {
        return false;
    }
}
