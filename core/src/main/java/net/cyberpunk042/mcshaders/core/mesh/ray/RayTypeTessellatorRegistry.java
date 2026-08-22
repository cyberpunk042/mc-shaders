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

import net.cyberpunk042.mcshaders.core.diag.Diag;
import net.cyberpunk042.mcshaders.core.shape.RayType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry mapping RayType values to their tessellator implementations.
 * 
 * <p>This provides a central location for registering and retrieving
 * ray type tessellators.
 * 
 * <h2>Usage</h2>
 * <pre>
 * RayTypeTessellator tess = RayTypeTessellatorRegistry.get(RayType.DROPLET);
 * tess.tessellate(builder, shape, context);
 * </pre>
 * 
 * @see RayTypeTessellator
 * @see RayType
 */
public final class RayTypeTessellatorRegistry {
    
    private static final Map<RayType, RayTypeTessellator> TESSELLATORS = new EnumMap<>(RayType.class);
    
    static {
        // Register default tessellators
        register(RayType.LINE, RayLineTessellator.INSTANCE);
        
        // 3D Spherical Ray Types - all use RaySphericalTessellator with SphereDeformation
        // The deformation type is selected per-RayType by RaySphericalTessellator
        
        // Basic Geometry Category
        register(RayType.DROPLET, RaySphericalTessellator.INSTANCE);
        register(RayType.CONE, RaySphericalTessellator.INSTANCE);
        register(RayType.ARROW, RaySphericalTessellator.INSTANCE);
        register(RayType.CAPSULE, RaySphericalTessellator.INSTANCE);
        
        // Organic/Natural Shapes (added for 3D rendering)
        register(RayType.SPHERE, RaySphericalTessellator.INSTANCE);
        register(RayType.SPHEROID, RaySphericalTessellator.INSTANCE);
        register(RayType.OVOID, RaySphericalTessellator.INSTANCE);
        register(RayType.EGG, RaySphericalTessellator.INSTANCE);
        register(RayType.PEAR, RaySphericalTessellator.INSTANCE);
        register(RayType.BULLET, RaySphericalTessellator.INSTANCE);
        
        // Energy Effects Category
        register(RayType.KAMEHAMEHA, RaySphericalTessellator.INSTANCE);
        register(RayType.LASER, RaySphericalTessellator.INSTANCE);
        register(RayType.FIRE_JET, RaySphericalTessellator.INSTANCE);
        register(RayType.PLASMA, RaySphericalTessellator.INSTANCE);
        
        // Particle Types
        register(RayType.BEADS, RaySphericalTessellator.INSTANCE);
        
        // Organic Types
        register(RayType.TENDRIL, RaySphericalTessellator.INSTANCE);
        register(RayType.SPINE, RaySphericalTessellator.INSTANCE);
        register(RayType.ROOT, RaySphericalTessellator.INSTANCE);
        
        // Types that need special handling stay as LINE for now
        // (LIGHTNING, CUBES, STARS, CRYSTALS need procedural generation)
        
        // Fill remaining with LINE fallback
        for (RayType type : RayType.values()) {
            if (!TESSELLATORS.containsKey(type)) {
                TESSELLATORS.put(type, RayLineTessellator.INSTANCE);
            }
        }
        
        Diag.FIELD.topic("tessellation").debug(
            "RayTypeTessellatorRegistry initialized with {} entries", TESSELLATORS.size());
    }
    
    private RayTypeTessellatorRegistry() {} // Utility class
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the tessellator for the specified ray type.
     * 
     * @param type The ray type
     * @return The tessellator (never null, falls back to LINE if not registered)
     */
    public static RayTypeTessellator get(RayType type) {
        if (type == null) {
            return RayLineTessellator.INSTANCE;
        }
        return TESSELLATORS.getOrDefault(type, RayLineTessellator.INSTANCE);
    }
    
    /**
     * Registers a tessellator for a ray type.
     * 
     * @param type The ray type
     * @param tessellator The tessellator implementation
     */
    public static void register(RayType type, RayTypeTessellator tessellator) {
        if (type != null && tessellator != null) {
            TESSELLATORS.put(type, tessellator);
            Diag.FIELD.topic("tessellation").debug(
                "Registered tessellator {} for RayType.{}", tessellator.name(), type);
        }
    }
    
    /**
     * Checks if a specific tessellator is registered (not just fallback).
     */
    public static boolean isImplemented(RayType type) {
        if (type == null || type == RayType.LINE) {
            return true;
        }
        RayTypeTessellator tess = TESSELLATORS.get(type);
        return tess != null && tess != RayLineTessellator.INSTANCE;
    }
    
    /**
     * Returns all registered ray types.
     */
    public static RayType[] registeredTypes() {
        return TESSELLATORS.keySet().toArray(new RayType[0]);
    }
}
