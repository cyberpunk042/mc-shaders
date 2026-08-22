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
 * Cone shape - a circular base tapering to a point (or smaller circle).
 * 
 * <p>When topRadius == 0, this is a true cone. When topRadius > 0,
 * this is a truncated cone (frustum).
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "shape": {
 *   "type": "cone",
 *   "bottomRadius": 1.0,
 *   "topRadius": 0.0,      // 0 for true cone, > 0 for frustum
 *   "height": 2.0,
 *   "segments": 32,
 *   "openBottom": false,   // Whether bottom is open
 *   "openTop": true        // Whether top is open (ignored if topRadius == 0)
 * }
 * </pre>
 * 
 * <h2>Parts</h2>
 * <ul>
 *   <li><b>surface</b> (TRIANGLE/QUAD) - Main cone surface</li>
 *   <li><b>bottomCap</b> (TRIANGLE) - Bottom circle (if not open)</li>
 *   <li><b>topCap</b> (TRIANGLE) - Top circle (if frustum and not open)</li>
 * </ul>
 * 
 * @see Shape
 * @see CylinderShape
 */
public record ConeShape(
    @Range(ValueRange.RADIUS) float bottomRadius,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float topRadius,
    @Range(ValueRange.POSITIVE) float height,
    @Range(ValueRange.STEPS) int segments,
    @JsonField(skipIfDefault = true) boolean openBottom,
    @JsonField(skipIfDefault = true, defaultValue = "true") boolean openTop
) implements Shape {
    
    /** Default cone (1.0 bottom radius, pointed top). */
    public static final ConeShape DEFAULT = new ConeShape(
        1.0f, 0f, 2.0f, 32, false, true);
    
    /** Frustum (truncated cone). */
    public static final ConeShape FRUSTUM = new ConeShape(
        1.0f, 0.5f, 2.0f, 32, false, false);
    
    /** Low-poly cone. */
    public static final ConeShape LOW_POLY = new ConeShape(
        1.0f, 0f, 2.0f, 8, false, true);
    
    /**
     * Creates a cone with specified dimensions.
     * @param bottomRadius Base radius
     * @param height Cone height
     */
    public static ConeShape of(float bottomRadius, float height) {
        return new ConeShape(bottomRadius, 0f, height, 32, false, true);
    }
    
    /**
     * Creates a frustum (truncated cone).
     * @param bottomRadius Base radius
     * @param topRadius Top radius
     * @param height Frustum height
     */
    public static ConeShape frustum(float bottomRadius, float topRadius, float height) {
        return new ConeShape(bottomRadius, topRadius, height, 32, false, false);
    }
    
    @Override
    public String getType() {
        return "cone";
    }
    
    @Override
    public Vector3f getBounds() {
        float maxR = Math.max(bottomRadius, topRadius);
        float d = maxR * 2;
        return new Vector3f(d, height, d);
    }
    
    @Override
    public CellType primaryCellType() {
        // True cone uses triangles for surface, frustum uses quads
        return topRadius > 0 ? CellType.QUAD : CellType.TRIANGLE;
    }
    
    @Override
    public Map<String, CellType> getParts() {
        return Map.of(
            "surface", primaryCellType(),
            "bottomCap", CellType.TRIANGLE,
            "topCap", CellType.TRIANGLE
        );
    }
    
    @Override
    public float getRadius() {
        return Math.max(bottomRadius, topRadius);
    }
    
    /** Whether this is a true pointed cone (vs frustum). */
    public boolean isPointed() {
        return topRadius <= 0.001f;
    }
    
    

    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    /** Create a builder pre-populated with this shape's values. */
    public Builder toBuilder() {
        return new Builder()
            .bottomRadius(bottomRadius)
            .topRadius(topRadius)
            .height(height)
            .segments(segments)
            .openBottom(openBottom)
            .openTop(openTop);
    }
    
    public static class Builder {
        private float bottomRadius = 1.0f;
        private float topRadius = 0f;
        private float height = 2.0f;
        private int segments = 32;
        private boolean openBottom = false;
        private boolean openTop = true;
        
        public Builder bottomRadius(float r) { this.bottomRadius = r; return this; }
        public Builder topRadius(float r) { this.topRadius = r; return this; }
        public Builder height(float h) { this.height = h; return this; }
        public Builder segments(int s) { this.segments = s; return this; }
        public Builder openBottom(boolean o) { this.openBottom = o; return this; }
        public Builder openTop(boolean o) { this.openTop = o; return this; }
        
        public ConeShape build() {
            return new ConeShape(bottomRadius, topRadius, height, segments, openBottom, openTop);
        }
    }
}

