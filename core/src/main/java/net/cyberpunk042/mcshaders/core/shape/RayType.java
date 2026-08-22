/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 *
 * JSON binding was removed on the way across: the model stays free of
 * serialisation so it can be loaded from external content. The @JsonField
 * metadata is retained for a codec layer above core.
 */
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Defines the visual appearance/geometry type of a ray.
 * 
 * <p>This controls WHAT the ray looks like (droplet, arrow, beads, etc.),
 * not how it's bent ({@link RayLineShape}) or arranged ({@link RayArrangement}).
 * 
 * <h2>Categories</h2>
 * <ul>
 *   <li><b>Basic Geometry</b>: LINE, DROPLET, CONE, ARROW, CAPSULE</li>
 *   <li><b>Energy/Effect</b>: KAMEHAMEHA, LASER, LIGHTNING, FIRE_JET, PLASMA</li>
 *   <li><b>Particle/Object</b>: BEADS, CUBES, STARS, CRYSTALS</li>
 *   <li><b>Organic</b>: TENDRIL, SPINE, ROOT</li>
 * </ul>
 * 
 * @see RaysShape
 * @see RayLineShape
 */
public enum RayType {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Basic Geometry
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Default flat ribbon ray. Simple quad/line. */
    LINE("Line", "Default flat ribbon rays", Category.BASIC),
    
    /** Teardrop shape - fat at base, pointy at tip. */
    DROPLET("Droplet", "Teardrop shape - fat at base, pointy at tip", Category.BASIC),
    
    /** 3D cone - circular base tapering to a point. */
    CONE("Cone", "3D cone - circular base tapering to point", Category.BASIC),
    
    /** Line with triangular arrowhead at the tip. */
    ARROW("Arrow", "Line with arrowhead at tip", Category.BASIC),
    
    /** Pill/capsule shape - cylinder with rounded hemisphere ends. */
    CAPSULE("Capsule", "Pill shape - cylinder with rounded ends", Category.BASIC),
    
    /** Perfect sphere - uniform radius in all directions. */
    SPHERE("Sphere", "Perfect sphere - uniform radius", Category.BASIC),
    
    /** Spheroid - sphere stretched (prolate) or squashed (oblate) along axis. */
    SPHEROID("Spheroid", "Sphere stretched or squashed along axis", Category.BASIC),
    
    /** Ovoid - smooth egg-like shape with gentle asymmetry. */
    OVOID("Ovoid", "Smooth egg-like shape", Category.BASIC),
    
    /** Egg - asymmetric egg shape (fatter at bottom). */
    EGG("Egg", "Egg shape - fatter at bottom", Category.BASIC),
    
    /** Pear - strong base mass with tapered top. */
    PEAR("Pear", "Pear shape - wide base, narrow top", Category.BASIC),
    
    /** Bullet - hemisphere tip with cylindrical body and flat base. */
    BULLET("Bullet", "Bullet shape - rounded tip, flat base", Category.BASIC),
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Energy/Effect
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Wide energy beam with glowing edges and spherical end (DBZ style). */
    KAMEHAMEHA("Kamehameha", "Wide energy beam with spherical end", Category.ENERGY),
    
    /** Thin concentrated beam with bright core and subtle glow. */
    LASER("Laser", "Thin concentrated beam with bright core", Category.ENERGY),
    
    /** Procedural jagged zigzag bolt. */
    LIGHTNING("Lightning", "Procedural jagged zigzag bolt", Category.ENERGY),
    
    /** Flame effect - wider at base, flickering edges. */
    FIRE_JET("Fire Jet", "Flame - wider at base, flickering edges", Category.ENERGY),
    
    /** Unstable energy - pulsing width, organic movement. */
    PLASMA("Plasma", "Unstable energy - pulsing, organic movement", Category.ENERGY),
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Particle/Object
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Chain of spheres along the ray path. */
    BEADS("Beads", "Chain of spheres along the ray path", Category.PARTICLE),
    
    /** Chain of cubes/blocks along the path. */
    CUBES("Cubes", "Chain of cubes/blocks along the path", Category.PARTICLE),
    
    /** Multi-pointed star shapes along the path. */
    STARS("Stars", "Multi-pointed star shapes", Category.PARTICLE),
    
    /** Faceted gem/diamond/crystal shapes along the path. */
    CRYSTALS("Crystals", "Faceted gem/diamond shapes", Category.PARTICLE),
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Organic
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Vine/tentacle - curves, tapers, may branch. */
    TENDRIL("Tendril", "Vine/tentacle - curves, tapers, may branch", Category.ORGANIC),
    
    /** Bone-like - segmented vertebrae appearance. */
    SPINE("Spine", "Bone-like - segmented vertebrae appearance", Category.ORGANIC),
    
    /** Tree root - branches, organic curves. */
    ROOT("Root", "Tree root - branches, organic curves", Category.ORGANIC);
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Fields
    // ═══════════════════════════════════════════════════════════════════════════
    
    private final String displayName;
    private final String description;
    private final Category category;
    
    RayType(String displayName, String description, Category category) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Accessors
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Human-readable display name for UI. */
    public String displayName() { return displayName; }
    
    /** Longer description for tooltips/info text. */
    public String description() { return description; }
    
    /** Category for grouping in UI. */
    public Category category() { return category; }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Whether this type requires 3D tessellation (vs simple 2D quads).
     * Simple types can use line primitives; complex types need triangles.
     */
    public boolean is3D() {
        return switch (this) {
            case LINE -> false;  // Flat ribbon
            case DROPLET, CONE, ARROW, CAPSULE, BULLET -> true;
            case SPHERE, SPHEROID, OVOID, EGG, PEAR -> true;  // Organic shapes
            case KAMEHAMEHA, LASER, LIGHTNING, FIRE_JET, PLASMA -> true;
            case BEADS, CUBES, STARS, CRYSTALS -> true;
            case TENDRIL, SPINE, ROOT -> true;
        };
    }
    
    /**
     * Whether this type has procedural/animated geometry.
     * These types may need per-frame tessellation.
     */
    public boolean isProcedural() {
        return switch (this) {
            case LIGHTNING, FIRE_JET, PLASMA -> true;
            case TENDRIL, ROOT -> true;
            default -> false;
        };
    }
    
    /**
     * Suggested minimum segments for this type to look good.
     */
    public int suggestedMinSegments() {
        return switch (this) {
            case LINE, ARROW -> 1;
            case DROPLET, CONE, CAPSULE, BULLET -> 8;
            case SPHERE, SPHEROID, OVOID, EGG, PEAR -> 8;  // Organic shapes
            case KAMEHAMEHA, LASER -> 12;
            case BEADS, CUBES, STARS, CRYSTALS -> 1;  // Discrete objects
            case LIGHTNING -> 16;
            case FIRE_JET, PLASMA -> 24;
            case TENDRIL, SPINE -> 16;
            case ROOT -> 32;
        };
    }
    
    /**
     * Parses from string, case-insensitive.
     * @param value String to parse
     * @return Matching type or LINE if not found
     */
    public static RayType fromString(String value) {
        if (value == null || value.isEmpty()) return LINE;
        try {
            return valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return LINE;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Category Enum
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Category for grouping ray types in UI. */
    public enum Category {
        BASIC("Basic Geometry"),
        ENERGY("Energy/Effect"),
        PARTICLE("Particle/Object"),
        ORGANIC("Organic");
        
        private final String displayName;
        
        Category(String displayName) {
            this.displayName = displayName;
        }
        
        public String displayName() { return displayName; }
    }
}
