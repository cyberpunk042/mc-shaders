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
 * Ring/torus shape - a band with inner and outer radius.
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "shape": {
 *   "type": "ring",
 *   "innerRadius": 0.8,
 *   "outerRadius": 1.0,
 *   "segments": 64,
 *   "y": 0.0,
 *   "arcStart": 0,
 *   "arcEnd": 360,
 *   "height": 0.0,
 *   "twist": 0.0,
 *   "taper": 1.0,
 *   "orientation": "POS_Y",
 *   "originOffset": 0.0
 * }
 * </pre>
 * 
 * <h2>Taper</h2>
 * <p>When height > 0, taper controls the top radii relative to bottom:</p>
 * <ul>
 *   <li>taper = 1.0 → no taper (cylinder)</li>
 *   <li>taper < 1.0 → narrower at top (cone-like)</li>
 *   <li>taper > 1.0 → wider at top (flared)</li>
 *   <li>taper = 0.0 → point at top (full cone)</li>
 * </ul>
 * 
 * <h2>Orientation</h2>
 * <p>The orientation axis controls which direction the ring's height extends:</p>
 * <ul>
 *   <li>POS_Y (default) - Standard vertical ring</li>
 *   <li>POS_Z - Horizontal ring pointing forward (e.g., beam)</li>
 *   <li>Other axes as needed</li>
 * </ul>
 * 
 * <h2>Parts</h2>
 * <ul>
 *   <li><b>surface</b> (SEGMENT) - Main ring surface</li>
 *   <li><b>innerEdge</b> (EDGE) - Inner border</li>
 *   <li><b>outerEdge</b> (EDGE) - Outer border</li>
 * </ul>
 * 
 * @see Shape
 * @see CellType
 * @see OrientationAxis
 */
public record RingShape(
    @Range(ValueRange.RADIUS) float innerRadius,
    @Range(ValueRange.RADIUS) float outerRadius,
    @Range(ValueRange.STEPS) int segments,
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true, defaultValue = "1") int heightSegments,
    @Range(ValueRange.UNBOUNDED) @JsonField(skipIfDefault = true) float y,
    @Range(ValueRange.DEGREES) @JsonField(skipIfDefault = true) float arcStart,
    @Range(ValueRange.DEGREES) @JsonField(skipIfDefault = true, defaultValue = "360") float arcEnd,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float height,
    @Range(ValueRange.DEGREES_FULL) @JsonField(skipIfDefault = true) float twist,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "1.0") float taper,
    @JsonField(skipIfDefault = true) OrientationAxis orientation,
    @Range(ValueRange.UNBOUNDED) @JsonField(skipIfDefault = true) float originOffset,
    @Range(ValueRange.ALPHA) @JsonField(skipIfDefault = true, defaultValue = "1.0") float bottomAlpha,
    @Range(ValueRange.ALPHA) @JsonField(skipIfDefault = true, defaultValue = "1.0") float topAlpha
)implements Shape {
    
    /** Default ring (0.8 inner, 1.0 outer). */
    public static final RingShape DEFAULT = new RingShape(
        0.8f, 1.0f, 64, 1, 0, 0, 360, 0, 0, 1.0f, null, 0, 1.0f, 1.0f);
    
    /** Thin ring. */
    public static final RingShape THIN = new RingShape(
        0.95f, 1.0f, 64, 1, 0, 0, 360, 0, 0, 1.0f, null, 0, 1.0f, 1.0f);
    
    /** Thick band. */
    public static final RingShape THICK = new RingShape(
        0.5f, 1.0f, 64, 1, 0, 0, 360, 0, 0, 1.0f, null, 0, 1.0f, 1.0f);
    
    /**
     * Creates a ring at the specified Y height.
     * @param innerRadius Inner radius
     * @param outerRadius Outer radius
     * @param y Y offset
     */
    public static RingShape at(@Range(ValueRange.RADIUS) float innerRadius, @Range(ValueRange.RADIUS) float outerRadius, @Range(ValueRange.UNBOUNDED) float y) {
        return new RingShape(innerRadius, outerRadius, 64, 1, y, 0, 360, 0, 0, 1.0f, null, 0, 1.0f, 1.0f);
    }
    
    /** Effective orientation (defaults to POS_Y if null). */
    public OrientationAxis effectiveOrientation() {
        return orientation != null ? orientation : OrientationAxis.POS_Y;
    }
    
    @Override
    public String getType() {
        return "ring";
    }
    
    @Override
    public Vector3f getBounds() {
        float maxR = Math.max(outerRadius, outerRadius * taper);
        float d = maxR * 2;
        return new Vector3f(d, Math.max(0.1f, height), d);
    }
    
    @Override
    public CellType primaryCellType() {
        return CellType.QUAD;
    }
    
    @Override
    public Map<String, CellType> getParts() {
        return Map.of(
            "surface", CellType.QUAD,
            "innerEdge", CellType.EDGE,
            "outerEdge", CellType.EDGE
        );
    }
    
    @Override
    public float getRadius() {
        return Math.max(outerRadius, outerRadius * taper);
    }
    
    /** Ring thickness (outer - inner). */
    public float thickness() {
        return outerRadius - innerRadius;
    }
    
    /** Whether this is a full ring or arc. */
    public boolean isFullRing() {
        return arcStart == 0 && arcEnd == 360;
    }
    
    /** Top inner radius (for 3D tapered rings). */
    public float topInnerRadius() {
        return innerRadius * taper;
    }
    
    /** Top outer radius (for 3D tapered rings). */
    public float topOuterRadius() {
        return outerRadius * taper;
    }
    
    /** Whether this ring has tapering. */
    public boolean hasTaper() {
        return Math.abs(taper - 1.0f) > 0.001f;
    }
    
    

    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    /** Create a builder pre-populated with this shape's values. */
    public Builder toBuilder() {
        return new Builder()
            .innerRadius(innerRadius)
            .outerRadius(outerRadius)
            .segments(segments)
            .heightSegments(heightSegments)
            .y(y)
            .arcStart(arcStart)
            .arcEnd(arcEnd)
            .height(height)
            .twist(twist)
            .taper(taper)
            .orientation(orientation)
            .originOffset(originOffset)
            .bottomAlpha(bottomAlpha)
            .topAlpha(topAlpha);
    }
    
    public static class Builder {
        private @Range(ValueRange.RADIUS) float innerRadius = 0.8f;
        private @Range(ValueRange.RADIUS) float outerRadius = 1.0f;
        private @Range(ValueRange.STEPS) int segments = 64;
        private @Range(ValueRange.STEPS) int heightSegments = 1;
        private @Range(ValueRange.UNBOUNDED) float y = 0;
        private @Range(ValueRange.DEGREES) float arcStart = 0;
        private @Range(ValueRange.DEGREES) float arcEnd = 360;
        private @Range(ValueRange.POSITIVE) float height = 0;
        private @Range(ValueRange.DEGREES_FULL) float twist = 0;
        private @Range(ValueRange.POSITIVE) float taper = 1.0f;
        private OrientationAxis orientation = null;
        private @Range(ValueRange.UNBOUNDED) float originOffset = 0;
        private @Range(ValueRange.ALPHA) float bottomAlpha = 1.0f;
        private @Range(ValueRange.ALPHA) float topAlpha = 1.0f;
        
        public Builder innerRadius(float r) { this.innerRadius = r; return this; }
        public Builder outerRadius(float r) { this.outerRadius = r; return this; }
        public Builder segments(int s) { this.segments = s; return this; }
        public Builder heightSegments(int s) { this.heightSegments = Math.max(1, s); return this; }
        public Builder y(@Range(ValueRange.UNBOUNDED) float y) { this.y = y; return this; }
        public Builder arcStart(float a) { this.arcStart = a; return this; }
        public Builder arcEnd(float a) { this.arcEnd = a; return this; }
        public Builder height(float h) { this.height = h; return this; }
        public Builder twist(float t) { this.twist = t; return this; }
        public Builder taper(float t) { this.taper = Math.max(0, t); return this; }
        public Builder orientation(OrientationAxis o) { this.orientation = o; return this; }
        public Builder originOffset(float o) { this.originOffset = o; return this; }
        public Builder bottomAlpha(float a) { this.bottomAlpha = Math.max(0, Math.min(1, a)); return this; }
        public Builder topAlpha(float a) { this.topAlpha = Math.max(0, Math.min(1, a)); return this; }
        
        public RingShape build() {
            return new RingShape(innerRadius, outerRadius, segments, heightSegments, y, arcStart, arcEnd, height, twist, taper, orientation, originOffset, bottomAlpha, topAlpha);
        }
    }
}
