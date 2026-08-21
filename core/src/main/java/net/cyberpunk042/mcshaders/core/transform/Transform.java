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
package net.cyberpunk042.mcshaders.core.transform;
import net.cyberpunk042.mcshaders.core.transform.Facing;

import org.joml.Vector3f;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Complete spatial transform configuration for a primitive.
 * 
 * <h2>Position</h2>
 * <ul>
 *   <li><b>anchor</b>: Base position (CENTER, FEET, HEAD, etc.)</li>
 *   <li><b>offset</b>: Additional offset from anchor</li>
 * </ul>
 * 
 * <h2>Rotation</h2>
 * <ul>
 *   <li><b>rotation</b>: Static rotation in degrees (pitch, yaw, roll)</li>
 *   <li><b>inheritRotation</b>: Whether to inherit layer rotation</li>
 * </ul>
 * 
 * <h2>Scale</h2>
 * <ul>
 *   <li><b>scale</b>: Uniform scale multiplier</li>
 *   <li><b>scaleXYZ</b>: Per-axis scale (optional)</li>
 *   <li><b>scaleWithRadius</b>: Scale with field baseRadius</li>
 * </ul>
 * 
 * <h2>Orientation</h2>
 * <ul>
 *   <li><b>facing</b>: Dynamic facing mode (FIXED, PLAYER_LOOK, etc.)</li>
 *   <li><b>up</b>: Up vector definition</li>
 *   <li><b>billboard</b>: Billboard mode for camera-facing</li>
 * </ul>
 * 
 * <h2>Dynamic Positioning</h2>
 * <ul>
 *   <li><b>orbit</b>: Legacy single-axis orbital motion</li>
 *   <li><b>orbit3d</b>: Multi-axis orbital motion (atom-like orbits)</li>
 * </ul>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "transform": {
 *   "anchor": "CENTER",
 *   "offset": [0, 0.5, 0],
 *   "rotation": [0, 45, 0],
 *   "scale": 1.5,
 *   "facing": "PLAYER_LOOK",
 *   "billboard": "Y_AXIS",
 *   "orbit": { "enabled": true, "radius": 2.0, "speed": 0.02 }
 * }
 * </pre>
 * 
 * @see Anchor
 * @see Facing
 * @see Billboard
 * @see UpVector
 * @see OrbitConfig
 * @see OrbitConfig3D
 */
public record Transform(
    @JsonField(skipIfEqualsConstant = "Anchor.CENTER") // Position
    Anchor anchor,
    @JsonField(skipIfNull = true) Vector3f offset,
    // Rotation
    @JsonField(skipIfNull = true) Vector3f rotation,
    @JsonField(skipIfDefault = true, defaultValue = "true") boolean inheritRotation,
    // Scale
    @Range(ValueRange.SCALE) @JsonField(skipIfEqualsConstant = "1.0f") float scale,
    @JsonField(skipIfNull = true) Vector3f scaleXYZ,
    @JsonField(skipIfDefault = true) boolean scaleWithRadius,
    @JsonField(skipIfEqualsConstant = "Facing.FIXED") // Orientation
    Facing facing,
    @JsonField(skipIfEqualsConstant = "UpVector.WORLD_UP") UpVector up,
    @JsonField(skipIfEqualsConstant = "Billboard.NONE") Billboard billboard,
    // Dynamic
    @JsonField(skipIfNull = true) OrbitConfig orbit,
    @JsonField(skipIfNull = true) OrbitConfig3D orbit3d
){
    // =========================================================================
    // Static Constants
    // =========================================================================
    
    /** Identity transform (CENTER anchor, no offset, no rotation, scale=1). */
    public static final Transform IDENTITY = new Transform(
        Anchor.CENTER, null, null, true, 1.0f, null, false,
        Facing.FIXED, UpVector.WORLD_UP, Billboard.NONE, null, null);
    
    /** Feet-anchored transform. */
    public static final Transform AT_FEET = new Transform(
        Anchor.FEET, null, null, true, 1.0f, null, false,
        Facing.FIXED, UpVector.WORLD_UP, Billboard.NONE, null, null);
    
    /** Head-anchored transform. */
    public static final Transform AT_HEAD = new Transform(
        Anchor.HEAD, null, null, true, 1.0f, null, false,
        Facing.FIXED, UpVector.WORLD_UP, Billboard.NONE, null, null);
    
    /** Billboard transform (always faces camera). */
    public static final Transform BILLBOARD = new Transform(
        Anchor.CENTER, null, null, true, 1.0f, null, false,
        Facing.FIXED, UpVector.WORLD_UP, Billboard.FULL, null, null);
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /**
     * Creates an identity transform.
     */
    public static Transform identity() {
        return IDENTITY;
    }
    
    /**
     * Creates a transform at the specified anchor.
     * @param anchor Position anchor
     */
    public static Transform at(Anchor anchor) {
        return new Transform(anchor, null, null, true, 1.0f, null, false,
            Facing.FIXED, UpVector.WORLD_UP, Billboard.NONE, null, null);
    }
    
    /**
     * Creates a transform with offset from CENTER.
     * @param x X offset
     * @param y Y offset
     * @param z Z offset
     */
    public static Transform offset(float x, float y, float z) {
        return new Transform(Anchor.CENTER, new Vector3f(x, y, z), null, true, 1.0f, null, false,
            Facing.FIXED, UpVector.WORLD_UP, Billboard.NONE, null, null);
    }
    
    /**
     * Creates a scaled transform.
     * @param scale Uniform scale
     */
    public static Transform scaled(@Range(ValueRange.SCALE) float scale) {
        return new Transform(Anchor.CENTER, null, null, true, scale, null, false,
            Facing.FIXED, UpVector.WORLD_UP, Billboard.NONE, null, null);
    }
    
    /**
     * Creates a rotated transform.
     * @param pitch X rotation in degrees
     * @param yaw Y rotation in degrees
     * @param roll Z rotation in degrees
     */
    public static Transform rotated(float pitch, float yaw, float roll) {
        return new Transform(Anchor.CENTER, null, new Vector3f(pitch, yaw, roll), true, 1.0f, null, false,
            Facing.FIXED, UpVector.WORLD_UP, Billboard.NONE, null, null);
    }
    
    // =========================================================================
    // Convenience Methods
    // =========================================================================
    
    /** Gets the effective offset (anchor offset + additional offset). */
    public Vector3f getEffectiveOffset() {
        Vector3f result = anchor.getOffset();
        if (offset != null) {
            result.add(offset);
        }
        return result;
    }
    
    /** Gets the effective rotation (or zero if null). */
    public Vector3f getEffectiveRotation() {
        return rotation != null ? new Vector3f(rotation) : new Vector3f();
    }
    
    /** Gets the effective scale vector. */
    public Vector3f getEffectiveScaleXYZ() {
        if (scaleXYZ != null) {
            return new Vector3f(scaleXYZ);
        }
        return new Vector3f(scale, scale, scale);
    }
    
    /** Whether this transform has legacy orbit enabled. */
    public boolean hasOrbit() {
        return orbit != null && orbit.isActive();
    }
    
    /** Whether this transform has 3D orbit enabled. */
    public boolean hasOrbit3D() {
        return orbit3d != null && orbit3d.isActive();
    }
    
    /** Whether this transform has any orbit (legacy or 3D). */
    public boolean hasAnyOrbit() {
        return hasOrbit() || hasOrbit3D();
    }
    
    /** Whether this transform uses billboard. */
    public boolean hasBillboard() {
        return billboard != Billboard.NONE;
    }
    
    /** Whether this transform has dynamic facing. */
    public boolean hasDynamicFacing() {
        return facing != Facing.FIXED;
    }
    
    // =========================================================================
    // Immutable Modifiers (return new instances)
    // =========================================================================
    
    /**
     * Returns a new Transform with the specified offset.
     */
    public Transform withOffset(Vector3f newOffset) {
        return new Transform(anchor, newOffset, rotation, inheritRotation, scale,
            scaleXYZ, scaleWithRadius, facing, up, billboard, orbit, orbit3d);
    }
    
    /**
     * Returns a new Transform with the specified uniform scale.
     */
    public Transform withScale(float newScale) {
        return new Transform(anchor, offset, rotation, inheritRotation, newScale,
            scaleXYZ, scaleWithRadius, facing, up, billboard, orbit, orbit3d);
    }
    
    /**
     * Returns a new Transform with the specified rotation.
     */
    public Transform withRotation(Vector3f newRotation) {
        return new Transform(anchor, offset, newRotation, inheritRotation, scale,
            scaleXYZ, scaleWithRadius, facing, up, billboard, orbit, orbit3d);
    }
    
    /**
     * Returns a new Transform with the specified anchor.
     */
    public Transform withAnchor(Anchor newAnchor) {
        return new Transform(newAnchor, offset, rotation, inheritRotation, scale,
            scaleXYZ, scaleWithRadius, facing, up, billboard, orbit, orbit3d);
    }
    
    /**
     * Returns a new Transform with the specified 3D orbit config.
     */
    public Transform withOrbit3D(OrbitConfig3D newOrbit3d) {
        return new Transform(anchor, offset, rotation, inheritRotation, scale,
            scaleXYZ, scaleWithRadius, facing, up, billboard, orbit, newOrbit3d);
    }
    
    /**
     * Gets the offset, never null (returns zero vector if null).
     */
    public Vector3f offset() {
        return offset != null ? offset : new Vector3f();
    }

    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .anchor(anchor)
            .offset(offset)
            .offset(offset)
            .rotation(rotation)
            .rotation(rotation)
            .inheritRotation(inheritRotation)
            .scale(scale)
            .scaleXYZ(scaleXYZ)
            .scaleXYZ(scaleXYZ)
            .scaleWithRadius(scaleWithRadius)
            .facing(facing)
            .up(up)
            .billboard(billboard)
            .orbit(orbit)
            .orbit3d(orbit3d);
    }
    
    public static class Builder {
        private Anchor anchor = Anchor.CENTER;
        private Vector3f offset = null;
        private Vector3f rotation = null;
        private boolean inheritRotation = true;
        private @Range(ValueRange.SCALE) float scale = 1.0f;
        private Vector3f scaleXYZ = null;
        private boolean scaleWithRadius = false;
        private Facing facing = Facing.FIXED;
        private UpVector up = UpVector.WORLD_UP;
        private Billboard billboard = Billboard.NONE;
        private OrbitConfig orbit = null;
        private OrbitConfig3D orbit3d = null;
        
        public Builder anchor(Anchor a) { this.anchor = a; return this; }
        public Builder offset(Vector3f o) { this.offset = o; return this; }
        public Builder offset(float x, float y, float z) { this.offset = new Vector3f(x, y, z); return this; }
        public Builder rotation(Vector3f r) { this.rotation = r; return this; }
        public Builder rotation(float pitch, float yaw, float roll) { 
            this.rotation = new Vector3f(pitch, yaw, roll); return this; 
        }
        public Builder inheritRotation(boolean i) { this.inheritRotation = i; return this; }
        public Builder scale(float s) { this.scale = s; return this; }
        public Builder scaleXYZ(Vector3f s) { this.scaleXYZ = s; return this; }
        public Builder scaleXYZ(float x, float y, float z) { this.scaleXYZ = new Vector3f(x, y, z); return this; }
        public Builder scaleWithRadius(boolean s) { this.scaleWithRadius = s; return this; }
        public Builder facing(Facing f) { this.facing = f; return this; }
        public Builder up(UpVector u) { this.up = u; return this; }
        public Builder billboard(Billboard b) { this.billboard = b; return this; }
        public Builder orbit(OrbitConfig o) { this.orbit = o; return this; }
        public Builder orbit3d(OrbitConfig3D o) { this.orbit3d = o; return this; }
        
        /** Convenience: create a horizontal orbit. */
        public Builder horizontalOrbit(float radius, float frequency) {
            this.orbit3d = OrbitConfig3D.horizontalOrbit(radius, frequency);
            return this;
        }
        
        /** Convenience: create a tilted orbit with wobble. */
        public Builder tiltedOrbit(float radius, float frequency, float wobbleAmp) {
            this.orbit3d = OrbitConfig3D.tiltedOrbit(radius, frequency, wobbleAmp);
            return this;
        }
        
        public Transform build() {
            return new Transform(anchor, offset, rotation, inheritRotation, scale, scaleXYZ,
                scaleWithRadius, facing, up, billboard, orbit, orbit3d);
        }
    }
}
