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

import net.cyberpunk042.mcshaders.core.pattern.CellType;
import org.joml.Vector3f;

import java.util.Map;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Capsule shape - a cylinder with hemispherical caps.
 * 
 * <p>Also known as a stadium or discorectangle when 2D. Useful for
 * smooth-edged tubes and elongated spheres.
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "shape": {
 *   "type": "capsule",
 *   "radius": 0.5,
 *   "height": 2.0,         // Total height including caps
 *   "segments": 32,        // Around circumference
 *   "rings": 8             // On each hemisphere
 * }
 * </pre>
 * 
 * <h2>Parts</h2>
 * <ul>
 *   <li><b>cylinder</b> (QUAD) - Main cylindrical body</li>
 *   <li><b>topCap</b> (TRIANGLE) - Top hemisphere</li>
 *   <li><b>bottomCap</b> (TRIANGLE) - Bottom hemisphere</li>
 * </ul>
 * 
 * @see Shape
 * @see CylinderShape
 * @see SphereShape
 */
public record CapsuleShape(
    @Range(ValueRange.RADIUS) float radius,
    @Range(ValueRange.POSITIVE) float height,
    @Range(ValueRange.STEPS) int segments,
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true, defaultValue = "8") int rings
) implements Shape {
    
    /** Default capsule (0.5 radius, 2.0 height). */
    public static final CapsuleShape DEFAULT = new CapsuleShape(
        0.5f, 2.0f, 32, 8);
    
    /** Thin capsule (0.25 radius, 2.0 height). */
    public static final CapsuleShape THIN = new CapsuleShape(
        0.25f, 2.0f, 24, 6);
    
    /** Fat capsule (0.75 radius, 1.5 height). */
    public static final CapsuleShape FAT = new CapsuleShape(
        0.75f, 1.5f, 32, 10);
    
    /**
     * Creates a capsule with specified dimensions.
     * @param radius Capsule radius
     * @param height Total height
     */
    public static CapsuleShape of(float radius, float height) {
        return new CapsuleShape(radius, height, 32, 8);
    }
    
    @Override
    public String getType() {
        return "capsule";
    }
    
    @Override
    public Vector3f getBounds() {
        float d = radius * 2;
        return new Vector3f(d, height, d);
    }
    
    @Override
    public CellType primaryCellType() {
        return CellType.QUAD;
    }
    
    @Override
    public Map<String, CellType> getParts() {
        return Map.of(
            "cylinder", CellType.QUAD,
            "topCap", CellType.TRIANGLE,
            "bottomCap", CellType.TRIANGLE
        );
    }
    
    @Override
    public float getRadius() {
        return radius;
    }
    
    /** Height of just the cylindrical part (excluding caps). */
    public float cylinderHeight() {
        return Math.max(0, height - 2 * radius);
    }
    
    

    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    /** Create a builder pre-populated with this shape's values. */
    public Builder toBuilder() {
        return new Builder()
            .radius(radius)
            .height(height)
            .segments(segments)
            .rings(rings);
    }
    
    public static class Builder {
        private float radius = 0.5f;
        private float height = 2.0f;
        private int segments = 32;
        private int rings = 8;
        
        public Builder radius(float r) { this.radius = r; return this; }
        public Builder height(float h) { this.height = h; return this; }
        public Builder segments(int s) { this.segments = s; return this; }
        public Builder rings(int r) { this.rings = r; return this; }
        
        public CapsuleShape build() {
            return new CapsuleShape(radius, height, segments, rings);
        }
    }
}

