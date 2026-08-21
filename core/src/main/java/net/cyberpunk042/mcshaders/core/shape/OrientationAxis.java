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
 * Orientation axis for directed shapes like beams.
 * 
 * <p>Defines which axis the beam extends along, with positive/negative variants:</p>
 * <ul>
 *   <li><b>POS_X</b> - Beam extends in +X direction (right)</li>
 *   <li><b>NEG_X</b> - Beam extends in -X direction (left)</li>
 *   <li><b>POS_Y</b> - Beam extends in +Y direction (up)</li>
 *   <li><b>NEG_Y</b> - Beam extends in -Y direction (down)</li>
 *   <li><b>POS_Z</b> - Beam extends in +Z direction (forward) - DEFAULT</li>
 *   <li><b>NEG_Z</b> - Beam extends in -Z direction (backward)</li>
 * </ul>
 * 
 * @see KamehamehaShape
 */
public enum OrientationAxis {
    /** Beam extends in +X direction (right). */
    POS_X("Right (+X)", 1, 0, 0),
    
    /** Beam extends in -X direction (left). */
    NEG_X("Left (-X)", -1, 0, 0),
    
    /** Beam extends in +Y direction (up). */
    POS_Y("Up (+Y)", 0, 1, 0),
    
    /** Beam extends in -Y direction (down). */
    NEG_Y("Down (-Y)", 0, -1, 0),
    
    /** Beam extends in +Z direction (forward). Default for horizontal beams. */
    POS_Z("Forward (+Z)", 0, 0, 1),
    
    /** Beam extends in -Z direction (backward). */
    NEG_Z("Back (-Z)", 0, 0, -1);
    
    private final String displayName;
    private final int dx, dy, dz;
    
    OrientationAxis(String displayName, int dx, int dy, int dz) {
        this.displayName = displayName;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }
    
    public String displayName() { return displayName; }
    
    /** Direction X component (-1, 0, or 1). */
    public int dx() { return dx; }
    
    /** Direction Y component (-1, 0, or 1). */
    public int dy() { return dy; }
    
    /** Direction Z component (-1, 0, or 1). */
    public int dz() { return dz; }
    
    /**
     * Transforms local beam coordinates to world coordinates.
     * 
     * <p>In local space, the beam extends along +Y with the orb at origin.
     * This method transforms (localX, localY, localZ) to world coordinates
     * based on the chosen orientation axis.</p>
     * 
     * @param localX Local X (perpendicular to beam)
     * @param localY Local Y (along beam axis)
     * @param localZ Local Z (perpendicular to beam)
     * @param offset Distance offset from origin along beam axis
     * @return float[3] world coordinates {x, y, z}
     */
    public float[] transform(float localX, float localY, float localZ, float offset) {
        // localY is the beam axis direction
        // localX and localZ are perpendicular
        float beamDist = localY + offset;
        
        return switch (this) {
            case POS_X -> new float[] { beamDist, localZ, localX };
            case NEG_X -> new float[] { -beamDist, localZ, -localX };
            case POS_Y -> new float[] { localX, beamDist, localZ };
            case NEG_Y -> new float[] { localX, -beamDist, -localZ };
            case POS_Z -> new float[] { localX, localZ, beamDist };
            case NEG_Z -> new float[] { -localX, localZ, -beamDist };
        };
    }
    
    /**
     * Transform a vertex position from local beam space to world space.
     * 
     * @param x Local X coordinate
     * @param y Local Y coordinate (beam axis)  
     * @param z Local Z coordinate
     * @param offset Origin offset along beam axis
     */
    public org.joml.Vector3f transformVertex(float x, float y, float z, float offset) {
        float[] result = transform(x, y, z, offset);
        return new org.joml.Vector3f(result[0], result[1], result[2]);
    }
    
    /**
     * Transform a normal vector from local beam space to world space.
     * Same as position transform but without offset.
     */
    public org.joml.Vector3f transformNormal(float nx, float ny, float nz) {
        float[] result = transform(nx, ny, nz, 0);
        return new org.joml.Vector3f(result[0], result[1], result[2]);
    }
    
    @Override
    public String toString() { return displayName; }
}
