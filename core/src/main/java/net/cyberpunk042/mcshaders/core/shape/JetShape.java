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
 * Jet shape - dual opposing cones/tubes for relativistic jets.
 * 
 * <h2>Geometry</h2>
 * <p>A jet consists of two opposing cone/tube structures emanating from a center
 * point along the Y axis. Each jet can be a solid cone (tipRadius=0), truncated
 * cone (tipRadius &lt; baseRadius), or tube (tipRadius = baseRadius).</p>
 * 
 * <pre>
 *              topTipRadius
 *                   ║
 *        ━━━━━━━━━━━╬━━━━━━━━━━━  ← top tip (y = +length + gap/2)
 *         ╲         ║         ╱
 *          ╲        ║        ╱ ← outer wall
 *           ╲       ║       ╱
 *        ━━━━╲━━━━━━╬━━━━━━╱━━━━  ← baseRadius (y = +gap/2)
 *                   ║
 *                  GAP  ← separation
 *                   ║
 *        ━━━━╱━━━━━━╬━━━━━━╲━━━━  ← baseRadius (y = -gap/2)
 *           ╱       ║       ╲
 *          ╱        ║        ╲ ← outer wall (mirrored)
 *         ╱         ║         ╲
 *        ━━━━━━━━━━━╬━━━━━━━━━━━  ← bottom tip (y = -length - gap/2)
 *                   ║
 *             bottomTipRadius
 * </pre>
 * 
 * <h2>Hollow Jets</h2>
 * <p>When hollow=true, an inner wall is created with innerBaseRadius and 
 * innerTipRadius, similar to Ring's inner/outer radius pattern.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "shape": {
 *   "type": "jet",
 *   "length": 2.0,
 *   "baseRadius": 0.3,
 *   "topTipRadius": 0.0,
 *   "bottomTipRadius": 0.0,
 *   "segments": 16,
 *   "lengthSegments": 8,
 *   "dualJets": true,
 *   "gap": 0.0,
 *   "hollow": false,
 *   "innerBaseRadius": 0.2,
 *   "innerTipRadius": 0.0,
 *   "capBase": true,
 *   "capTip": false
 * }
 * </pre>
 * 
 * <h2>Parts</h2>
 * <ul>
 *   <li><b>outerWall</b> (QUAD) - Outer surface of the cone/tube</li>
 *   <li><b>innerWall</b> (QUAD) - Inner surface if hollow</li>
 *   <li><b>capBase</b> (SECTOR) - Base cap (ring if hollow)</li>
 *   <li><b>capTip</b> (SECTOR) - Tip cap (ring if hollow)</li>
 *   <li><b>edges</b> (EDGE) - Edge lines</li>
 * </ul>
 * 
 * @see Shape
 * @see CellType
 */
public record JetShape(
    // === Geometry ===
    @Range(ValueRange.POSITIVE_NONZERO) float length,
    @Range(ValueRange.RADIUS) float baseRadius,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfEqualsField = "baseRadius") float topTipRadius,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfEqualsField = "topTipRadius") float bottomTipRadius,
    @Range(ValueRange.STEPS) int segments,
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true, defaultValue = "1") int lengthSegments,
    
    // === Configuration ===
    @JsonField(skipIfDefault = true, defaultValue = "true") boolean dualJets,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float gap,
    
    // === Hollow Support (like Ring inner/outer) ===
    @JsonField(skipIfDefault = true) boolean hollow,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float innerBaseRadius,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float innerTipRadius,
    @JsonField(skipIfDefault = true) boolean unifiedInner,  // Use thickness instead of custom radii
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float innerWallThickness,  // Wall thickness when unified
    
    // === Caps ===
    @JsonField(skipIfDefault = true, defaultValue = "true") boolean capBase,
    @JsonField(skipIfDefault = true) boolean capTip,
    
    // === Alpha Gradient (along length) ===
    /** Base alpha at center (where jet originates). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1.0") float baseAlpha,
    /** Minimum alpha at base (floor for travel effects). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "0.0") float baseMinAlpha,
    /** Tip alpha at extremity (where jet ends). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1.0") float tipAlpha,
    /** Minimum alpha at tip (floor for travel effects). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "0.0") float tipMinAlpha
) implements Shape {
    
    // =========================================================================
    // Presets
    // =========================================================================
    
    /** Default jet (relativistic jet: narrow base, expands outward). */
    public static final JetShape DEFAULT = new JetShape(
        2.0f,           // length (moderate length)
        0.05f,          // baseRadius (narrow at center/origin)
        0.15f,          // topTipRadius (expands outward - CONE shape)
        0.15f,          // bottomTipRadius (expands outward - CONE shape)
        16,             // segments
        4,              // lengthSegments
        true,           // dualJets (bipolar jets)
        0.1f,           // gap (small separation at center)
        false,          // hollow
        0.0f,           // innerBaseRadius
        0.0f,           // innerTipRadius
        true,           // unifiedInner (use thickness mode by default)
        0.02f,          // innerWallThickness
        true,           // capBase
        true,           // capTip
        1.0f,           // baseAlpha
        0.0f,           // baseMinAlpha
        0.3f,           // tipAlpha (fade out at tips)
        0.0f            // tipMinAlpha
    );
    
    /** Pointed cone jet (classic sci-fi look). */
    public static final JetShape CONE = new JetShape(
        2.0f, 0.3f, 0.0f, 0.0f, 16, 1, true, 0.0f,
        false, 0.0f, 0.0f, true, 0.02f, true, false,
        1.0f, 0.0f, 0.0f, 0.0f);  // Full at base, invisible at pointed tip
    
    /** Tube jet (parallel sided beams). */
    public static final JetShape TUBE = new JetShape(
        3.0f, 0.2f, 0.2f, 0.2f, 16, 4, true, 0.2f,
        false, 0.0f, 0.0f, true, 0.02f, true, true,
        1.0f, 0.2f, 0.8f, 0.1f);  // Slight fade, high min alphas
    
    /** Hollow tube jet (like relativistic plasma jets). */
    public static final JetShape HOLLOW_TUBE = new JetShape(
        3.0f, 0.25f, 0.25f, 0.25f, 24, 4, true, 0.3f,
        true, 0.18f, 0.18f, false, 0.02f, true, true,
        1.0f, 0.3f, 0.6f, 0.2f);  // Plasma-like glow gradient
    
    /** Single upward jet (pointed cone). */
    public static final JetShape SINGLE_CONE = new JetShape(
        2.0f, 0.4f, 0.0f, 0.0f, 16, 1, false, 0.0f,
        false, 0.0f, 0.0f, true, 0.02f, true, false,
        1.0f, 0.0f, 0.0f, 0.0f);  // Fades to invisible at tip
    
    /** Wide flared jets (like pulsar beams). */
    public static final JetShape FLARED = new JetShape(
        2.5f, 0.15f, 0.4f, 0.4f, 24, 4, true, 0.2f,
        false, 0.0f, 0.0f, true, 0.02f, true, true,
        1.0f, 0.1f, 0.5f, 0.0f);  // Strong at base, fades at flared tips
    
    public static JetShape defaults() { return DEFAULT; }
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /**
     * Creates a simple dual cone jet.
     * @param length Length of each jet
     * @param baseRadius Radius at the base
     */
    public static JetShape cone(float length, float baseRadius) {
        return builder().length(length).baseRadius(baseRadius)
            .topTipRadius(0).bottomTipRadius(0).build();
    }
    
    /**
     * Creates a dual tube jet.
     * @param length Length of each jet
     * @param radius Uniform radius (base = tip)
     */
    public static JetShape tube(float length, float radius) {
        return builder().length(length).baseRadius(radius)
            .topTipRadius(radius).bottomTipRadius(radius)
            .capTip(true).build();
    }
    
    /**
     * Creates asymmetric jets (different top/bottom tip radii).
     */
    public static JetShape asymmetric(float length, float baseRadius,
                                       float topTipRadius, float bottomTipRadius) {
        return builder().length(length).baseRadius(baseRadius)
            .topTipRadius(topTipRadius).bottomTipRadius(bottomTipRadius).build();
    }
    
    // =========================================================================
    // Shape Interface
    // =========================================================================
    
    @Override
    public String getType() {
        return "jet";
    }
    
    @Override
    public Vector3f getBounds() {
        float maxR = Math.max(baseRadius, Math.max(topTipRadius, bottomTipRadius));
        float totalHeight = dualJets ? (length * 2 + gap) : length;
        return new Vector3f(maxR * 2, totalHeight, maxR * 2);
    }
    
    @Override
    public CellType primaryCellType() {
        return CellType.QUAD;
    }
    
    @Override
    public Map<String, CellType> getParts() {
        return Map.of(
            "outerWall", CellType.QUAD,
            "innerWall", CellType.QUAD,
            "capBase", CellType.SECTOR,
            "capTip", CellType.SECTOR,
            "edges", CellType.EDGE
        );
    }
    
    @Override
    public float getRadius() {
        return baseRadius;
    }
    
    // =========================================================================
    // Utility Methods
    // =========================================================================
    
    /** Whether this is a pointed cone (tip radius = 0). */
    public boolean isTopCone() {
        return topTipRadius == 0;
    }
    
    /** Whether the bottom jet is a pointed cone. */
    public boolean isBottomCone() {
        return bottomTipRadius == 0;
    }
    
    /** Whether both jets are identical. */
    public boolean isSymmetric() {
        return topTipRadius == bottomTipRadius;
    }
    
    /** Whether this is a tube (tip radius = base radius). */
    public boolean isTube() {
        return topTipRadius == baseRadius && bottomTipRadius == baseRadius;
    }
    
    /** Wall thickness for hollow jets. */
    public float wallThickness() {
        return hollow ? (baseRadius - innerBaseRadius) : baseRadius;
    }
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    /** Create a builder pre-populated with this shape's values. */
    public Builder toBuilder() {
        return new Builder()
            .length(length)
            .baseRadius(baseRadius)
            .topTipRadius(topTipRadius)
            .bottomTipRadius(bottomTipRadius)
            .segments(segments)
            .lengthSegments(lengthSegments)
            .dualJets(dualJets)
            .gap(gap)
            .hollow(hollow)
            .innerBaseRadius(innerBaseRadius)
            .innerTipRadius(innerTipRadius)
            .unifiedInner(unifiedInner)
            .innerWallThickness(innerWallThickness)
            .capBase(capBase)
            .capTip(capTip)
            .baseAlpha(baseAlpha)
            .baseMinAlpha(baseMinAlpha)
            .tipAlpha(tipAlpha)
            .tipMinAlpha(tipMinAlpha);
    }
    
    public static class Builder {
        private float length = 2.0f;
        private float baseRadius = 0.3f;
        private float topTipRadius = 0.0f;
        private float bottomTipRadius = 0.0f;
        private int segments = 16;
        private int lengthSegments = 1;
        private boolean dualJets = true;
        private float gap = 0.0f;
        private boolean hollow = false;
        private float innerBaseRadius = 0.0f;
        private float innerTipRadius = 0.0f;
        private boolean unifiedInner = true;
        private float innerWallThickness = 0.02f;
        private boolean capBase = true;
        private boolean capTip = false;
        private float baseAlpha = 1.0f;
        private float baseMinAlpha = 0.0f;
        private float tipAlpha = 1.0f;
        private float tipMinAlpha = 0.0f;
        
        public Builder length(float v) { this.length = v; return this; }
        public Builder baseRadius(float v) { this.baseRadius = v; return this; }
        public Builder topTipRadius(float v) { this.topTipRadius = v; return this; }
        public Builder bottomTipRadius(float v) { this.bottomTipRadius = v; return this; }
        public Builder segments(int v) { this.segments = v; return this; }
        public Builder lengthSegments(int v) { this.lengthSegments = v; return this; }
        public Builder dualJets(boolean v) { this.dualJets = v; return this; }
        public Builder gap(float v) { this.gap = v; return this; }
        public Builder hollow(boolean v) { this.hollow = v; return this; }
        public Builder innerBaseRadius(float v) { this.innerBaseRadius = v; return this; }
        public Builder innerTipRadius(float v) { this.innerTipRadius = v; return this; }
        public Builder unifiedInner(boolean v) { this.unifiedInner = v; return this; }
        public Builder innerWallThickness(float v) { this.innerWallThickness = v; return this; }
        public Builder capBase(boolean v) { this.capBase = v; return this; }
        public Builder baseAlpha(float v) { this.baseAlpha = v; return this; }
        public Builder baseMinAlpha(float v) { this.baseMinAlpha = v; return this; }
        public Builder tipAlpha(float v) { this.tipAlpha = v; return this; }
        public Builder tipMinAlpha(float v) { this.tipMinAlpha = v; return this; }
        public Builder capTip(boolean v) { this.capTip = v; return this; }
        
        /** Sets wall thickness for hollow jets (auto-calculates inner radii). */
        public Builder wallThickness(float thickness) {
            this.hollow = true;
            this.innerBaseRadius = Math.max(0, baseRadius - thickness);
            this.innerTipRadius = Math.max(0, topTipRadius - thickness);
            return this;
        }
        
        public JetShape build() {
            return new JetShape(
                length, baseRadius, topTipRadius, bottomTipRadius,
                segments, lengthSegments, dualJets, gap,
                hollow, innerBaseRadius, innerTipRadius,
                unifiedInner, innerWallThickness,
                capBase, capTip,
                baseAlpha, baseMinAlpha, tipAlpha, tipMinAlpha
            );
        }
    }
}
