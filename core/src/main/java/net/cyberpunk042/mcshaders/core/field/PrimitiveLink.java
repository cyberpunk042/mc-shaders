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
package net.cyberpunk042.mcshaders.core.field;

import net.cyberpunk042.mcshaders.core.animation.Axis;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Links a primitive to ONE target primitive for coordinated behavior.
 * 
 * <p>Per ARCHITECTURE §9: Primitives within a layer can be linked
 * for coordinated behavior like radius matching, position following,
 * mirroring, and animation phase offset.</p>
 * 
 * <h2>Structure</h2>
 * <p>Each link has ONE target and boolean flags for what to link:</p>
 * <table>
 *   <tr><th>Field</th><th>Description</th></tr>
 *   <tr><td>{@code target}</td><td>The primitive ID to link to (required)</td></tr>
 *   <tr><td>{@code radiusMatch}</td><td>Match target's radius</td></tr>
 *   <tr><td>{@code radiusOffset}</td><td>Offset from matched radius</td></tr>
 *   <tr><td>{@code follow}</td><td>Follow target's position</td></tr>
 *   <tr><td>{@code mirror}</td><td>Mirror on specified axis</td></tr>
 *   <tr><td>{@code phaseOffset}</td><td>Animation phase offset</td></tr>
 *   <tr><td>{@code scaleWith}</td><td>Scale proportionally with target</td></tr>
 *   <tr><td>{@code orbitSync}</td><td>Sync orbit config with target</td></tr>
 *   <tr><td>{@code orbitPhaseOffset}</td><td>Offset orbit phase</td></tr>
 *   <tr><td>{@code colorMatch}</td><td>Match target's color</td></tr>
 *   <tr><td>{@code alphaMatch}</td><td>Match target's alpha</td></tr>
 * </table>
 * 
 * <h2>Multiple Links</h2>
 * <p>A primitive can have multiple links (List&lt;PrimitiveLink&gt;). Each link
 * targets one primitive and defines how they're connected.</p>
 * 
 * <h2>Atom Orbital Example</h2>
 * <pre>
 * // 4 electrons linked to nucleus with orbit sync
 * electron1: links: [{ target: "nucleus", orbitSync: true, orbitPhaseOffset: 0.0 }]
 * electron2: links: [{ target: "nucleus", orbitSync: true, orbitPhaseOffset: 0.25 }]
 * electron3: links: [{ target: "nucleus", orbitSync: true, orbitPhaseOffset: 0.5 }]
 * electron4: links: [{ target: "nucleus", orbitSync: true, orbitPhaseOffset: 0.75 }]
 * </pre>
 * 
 * <h2>Cycle Prevention</h2>
 * <p>Links are resolved in primitive declaration order. A primitive can only
 * link to primitives declared BEFORE it. This prevents circular references.</p>
 * 
 * @see Primitive
 * @see LinkResolver
 */
public record PrimitiveLink(
    // Target (required)
    String target,
    
    // Position/Shape linking (boolean flags + offsets)
    @JsonField(skipIfDefault = true) boolean radiusMatch,
    @JsonField(skipIfDefault = true) float radiusOffset,
    @JsonField(skipIfDefault = true) boolean follow,
    @JsonField(skipIfDefault = true) boolean followDynamic,  // NEW: Follow animated position
    @JsonField(skipIfNull = true) Axis mirror,
    @JsonField(skipIfDefault = true) float phaseOffset,
    @JsonField(skipIfDefault = true) boolean scaleWith,
    
    // Orbit linking
    @JsonField(skipIfDefault = true) boolean orbitSync,
    @JsonField(skipIfDefault = true) float orbitPhaseOffset,
    @JsonField(skipIfDefault = true) float orbitRadiusOffset,      // NEW: Offset from target's orbit radius
    @JsonField(skipIfDefault = true) float orbitSpeedMult,         // NEW: Multiply target's orbit speed (default 1.0)
    @JsonField(skipIfDefault = true) float orbitInclinationOffset, // NEW: Add to target's inclination
    @JsonField(skipIfDefault = true) float orbitPrecessionOffset,  // NEW: Add to target's precession
    
    // Appearance linking
    @JsonField(skipIfDefault = true) boolean colorMatch,
    @JsonField(skipIfDefault = true) boolean alphaMatch
){
    
    /** No linking (null target). */
    public static final PrimitiveLink NONE = new PrimitiveLink(
        null, false, 0, false, false, null, 0, false, false, 0, 0, 1f, 0, 0, false, false);
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /** Creates a link to a target with default settings. */
    public static PrimitiveLink to(String targetId) {
        return new PrimitiveLink(targetId, false, 0, false, false, null, 0, false, false, 0, 0, 1f, 0, 0, false, false);
    }
    
    /** Creates a radius match link. */
    public static PrimitiveLink radiusMatch(String targetId, float offset) {
        return new PrimitiveLink(targetId, true, offset, false, false, null, 0, false, false, 0, 0, 1f, 0, 0, false, false);
    }
    
    /** Creates a follow link (static). */
    public static PrimitiveLink follow(String targetId) {
        return new PrimitiveLink(targetId, false, 0, true, false, null, 0, false, false, 0, 0, 1f, 0, 0, false, false);
    }
    
    /** Creates a dynamic follow link (follows animated position). */
    public static PrimitiveLink followDynamic(String targetId) {
        return new PrimitiveLink(targetId, false, 0, false, true, null, 0, false, false, 0, 0, 1f, 0, 0, false, false);
    }
    
    /** Creates a mirror link. */
    public static PrimitiveLink mirror(String targetId, Axis axis) {
        return new PrimitiveLink(targetId, false, 0, false, false, axis, 0, false, false, 0, 0, 1f, 0, 0, false, false);
    }
    
    /** Creates an orbit sync link with phase offset. */
    public static PrimitiveLink orbitSync(String targetId, float orbitPhaseOffset) {
        return new PrimitiveLink(targetId, false, 0, false, false, null, 0, false, true, orbitPhaseOffset, 0, 1f, 0, 0, false, false);
    }
    
    /** Creates a color/alpha match link. */
    public static PrimitiveLink appearance(String targetId, boolean color, boolean alpha) {
        return new PrimitiveLink(targetId, false, 0, false, false, null, 0, false, false, 0, 0, 1f, 0, 0, color, alpha);
    }
    
    // =========================================================================
    // Query Methods
    // =========================================================================
    
    /** Checks if this link has a valid target. */
    public boolean isValid() {
        return target != null && !target.isEmpty();
    }
    
    /** Checks if this link has any active link types. */
    public boolean hasAnyLinkType() {
        return radiusMatch || follow || followDynamic || mirror != null || scaleWith 
            || orbitSync || colorMatch || alphaMatch
            || phaseOffset != 0 || orbitPhaseOffset != 0 || radiusOffset != 0
            || orbitRadiusOffset != 0 || orbitSpeedMult != 1f 
            || orbitInclinationOffset != 0 || orbitPrecessionOffset != 0;
    }
    
    /** Checks if this link has orbit synchronization. */
    public boolean hasOrbitLink() {
        return orbitSync || orbitPhaseOffset != 0 || orbitRadiusOffset != 0 
            || orbitSpeedMult != 1f || orbitInclinationOffset != 0 || orbitPrecessionOffset != 0;
    }
    
    /** Checks if this link has position following. */
    public boolean hasFollow() {
        return follow || followDynamic;
    }
    
    /** Checks if this link has appearance linking. */
    public boolean hasAppearanceLink() {
        return colorMatch || alphaMatch;
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    /**
     * Starts a link to {@code target}.
     *
     * <p>The named factories above cover one constraint each — {@link #radiusMatch},
     * {@link #mirror}, {@link #orbitSync} and the rest. This is for the cases that
     * combine several, which the factories cannot express:
     *
     * <pre>
     * PrimitiveLink.builder("core")
     *         .radiusMatch(true).radiusOffset(0.5f)
     *         .colorMatch(true)
     *         .build();
     * </pre>
     *
     * <p>{@code Builder} was reachable before this only through
     * {@link #toBuilder()} on an existing link, which meant building one from nothing
     * started by making a link you did not want.
     *
     * @param target the id of the primitive to link to
     * @return a builder with the target set and every constraint off
     */
    public static Builder builder(String target) {
        return new Builder().target(target);
    }

    public Builder toBuilder() {
        return new Builder()
            .target(target)
            .radiusMatch(radiusMatch)
            .radiusOffset(radiusOffset)
            .follow(follow)
            .followDynamic(followDynamic)
            .mirror(mirror)
            .phaseOffset(phaseOffset)
            .scaleWith(scaleWith)
            .orbitSync(orbitSync)
            .orbitPhaseOffset(orbitPhaseOffset)
            .orbitRadiusOffset(orbitRadiusOffset)
            .orbitSpeedMult(orbitSpeedMult)
            .orbitInclinationOffset(orbitInclinationOffset)
            .orbitPrecessionOffset(orbitPrecessionOffset)
            .colorMatch(colorMatch)
            .alphaMatch(alphaMatch);
    }
    
    public static class Builder {
        private String target;
        private boolean radiusMatch;
        private float radiusOffset;
        private boolean follow;
        private boolean followDynamic;
        private Axis mirror;
        private float phaseOffset;
        private boolean scaleWith;
        private boolean orbitSync;
        private float orbitPhaseOffset;
        private float orbitRadiusOffset;
        private float orbitSpeedMult = 1f;  // Default to 1 (no change)
        private float orbitInclinationOffset;
        private float orbitPrecessionOffset;
        private boolean colorMatch;
        private boolean alphaMatch;
        
        public Builder target(String t) { this.target = t; return this; }
        public Builder radiusMatch(boolean v) { this.radiusMatch = v; return this; }
        public Builder radiusOffset(float v) { this.radiusOffset = v; return this; }
        public Builder follow(boolean v) { this.follow = v; return this; }
        public Builder followDynamic(boolean v) { this.followDynamic = v; return this; }
        public Builder mirror(Axis v) { this.mirror = v; return this; }
        public Builder phaseOffset(float v) { this.phaseOffset = v; return this; }
        public Builder scaleWith(boolean v) { this.scaleWith = v; return this; }
        public Builder orbitSync(boolean v) { this.orbitSync = v; return this; }
        public Builder orbitPhaseOffset(float v) { this.orbitPhaseOffset = v; return this; }
        public Builder orbitRadiusOffset(float v) { this.orbitRadiusOffset = v; return this; }
        public Builder orbitSpeedMult(float v) { this.orbitSpeedMult = v; return this; }
        public Builder orbitInclinationOffset(float v) { this.orbitInclinationOffset = v; return this; }
        public Builder orbitPrecessionOffset(float v) { this.orbitPrecessionOffset = v; return this; }
        public Builder colorMatch(boolean v) { this.colorMatch = v; return this; }
        public Builder alphaMatch(boolean v) { this.alphaMatch = v; return this; }
        
        public PrimitiveLink build() {
            return new PrimitiveLink(target, radiusMatch, radiusOffset, follow, followDynamic, mirror,
                phaseOffset, scaleWith, orbitSync, orbitPhaseOffset, orbitRadiusOffset, orbitSpeedMult,
                orbitInclinationOffset, orbitPrecessionOffset, colorMatch, alphaMatch);
        }
    }
    
    // =========================================================================
    // JSON Serialization
    // =========================================================================
    
    
    
    
}
