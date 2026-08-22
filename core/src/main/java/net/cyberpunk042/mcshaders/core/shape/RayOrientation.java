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
 * Controls how 3D ray shapes are oriented along the ray path.
 * 
 * <p>This defines the direction that 3D ray types (DROPLET, CONE, ARROW, etc.)
 * point. For example, a DROPLET can point outward from the field center,
 * or upward regardless of the ray direction.</p>
 * 
 * <h2>Visual Examples</h2>
 * <ul>
 *   <li><b>ALONG_RAY</b>: Droplet points from start to end (default)</li>
 *   <li><b>OUTWARD</b>: Droplet points away from field center</li>
 *   <li><b>UPWARD</b>: All droplets point up, like rain falling up</li>
 * </ul>
 * 
 * @see RaysShape
 * @see RayType
 */
public enum RayOrientation {
    
    /** Shape points in the ray direction (start → end). Default. */
    ALONG_RAY("Along Ray", "Shape points in ray direction (default)"),
    
    /** Shape points opposite to ray direction (end → start). */
    AGAINST_RAY("Against Ray", "Shape points opposite to ray direction"),
    
    /** Shape points away from field center (radially outward). */
    OUTWARD("Outward", "Shape points away from field center"),
    
    /** Shape points toward field center (radially inward). */
    INWARD("Inward", "Shape points toward field center"),
    
    /** Shape always points up (+Y axis). */
    UPWARD("Upward", "Shape points upward (+Y)"),
    
    /** Shape always points down (-Y axis). */
    DOWNWARD("Downward", "Shape points downward (-Y)"),
    
    /** Shape points in the direction of the tangent (perpendicular to radial). */
    TANGENT("Tangent", "Shape points tangent to radius (circular motion)");
    
    private final String displayName;
    private final String description;
    
    RayOrientation(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Human-readable display name for UI.
     */
    public String displayName() {
        return displayName;
    }
    
    /**
     * Description for tooltips.
     */
    public String description() {
        return description;
    }
    
    /**
     * Whether this orientation depends on the ray's position relative to center.
     * OUTWARD, INWARD, and TANGENT need center position to compute.
     */
    public boolean requiresCenterReference() {
        return this == OUTWARD || this == INWARD || this == TANGENT;
    }
    
    /**
     * Whether this is a fixed world-space direction (UPWARD, DOWNWARD).
     */
    public boolean isFixedDirection() {
        return this == UPWARD || this == DOWNWARD;
    }
    
    /**
     * Parses from string, case-insensitive.
     * @param value String to parse
     * @return Matching orientation or ALONG_RAY if not found
     */
    public static RayOrientation fromString(String value) {
        if (value == null || value.isEmpty()) return ALONG_RAY;
        try {
            return valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return ALONG_RAY;
        }
    }
}
