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
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import net.cyberpunk042.mcshaders.core.shape.RingShape;
import net.cyberpunk042.mcshaders.core.shape.CylinderShape;
import net.cyberpunk042.mcshaders.core.shape.PrismShape;
import net.cyberpunk042.mcshaders.core.transform.OrbitConfig3D;
import net.cyberpunk042.mcshaders.core.transform.Transform;
import org.joml.Vector3f;

import java.util.*;

/**
 * Resolves primitive links within a layer.
 * 
 * <p>Per ARCHITECTURE §9: Links are resolved in primitive declaration order.
 * A primitive can only link to primitives declared BEFORE it.
 * This makes circular references impossible by design.
 * 
 * <h2>Resolution Process</h2>
 * <ol>
 *   <li>Build index of primitives by ID</li>
 *   <li>For each primitive with links, validate references</li>
 *   <li>Compute final values based on link type</li>
 * </ol>
 * 
 * <h2>Error Handling</h2>
 * <ul>
 *   <li><b>Forward reference</b>: Logs error, link ignored</li>
 *   <li><b>Unknown ID</b>: Logs error, link ignored</li>
 *   <li><b>Self reference</b>: Logs error, link ignored</li>
 * </ul>
 * 
 * @see PrimitiveLink
 * @see Primitive
 */
public final class LinkResolver {
    
    private LinkResolver() {}
    
    // =========================================================================
    // Validation
    // =========================================================================
    
    /**
     * Validates links for a list of primitives.
     * 
     * <p>Checks that all referenced primitives:
     * <ul>
     *   <li>Exist in the list</li>
     *   <li>Are declared BEFORE the referencing primitive (no forward refs)</li>
     *   <li>Are not self-references</li>
     * </ul>
     * 
     * @param primitives Ordered list of primitives
     * @return List of validation errors (empty if valid)
     */
    public static List<String> validate(List<Primitive> primitives) {
        List<String> errors = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        
        for (int i = 0; i < primitives.size(); i++) {
            Primitive prim = primitives.get(i);
            String id = prim.id();
            
            // Check for duplicate IDs
            if (seenIds.contains(id)) {
                errors.add("Duplicate primitive ID: " + id);
            }
            seenIds.add(id);
            
            // Validate links
            PrimitiveLink link = prim.link();
            if (link != null && link.isValid() && link.hasAnyLinkType()) {
                String refId = link.target();
                
                // Self reference
                if (refId.equals(id)) {
                    errors.add("Primitive '" + id + "' references itself");
                } else if (!seenIds.contains(refId)) {
                    // Forward reference (not yet seen)
                    boolean existsLater = primitives.stream()
                        .skip(i + 1)
                        .anyMatch(p -> refId.equals(p.id()));
                    
                    if (existsLater) {
                        errors.add("Forward reference: '" + id + "' links to '" + refId + 
                            "' which is declared later (must be before)");
                    } else {
                        errors.add("Unknown reference: '" + id + "' links to '" + refId + 
                            "' which does not exist");
                    }
                }
            }
        }

        return errors;
    }
    
    /**
     * Checks if links are valid (no errors).
     */
    public static boolean isValid(List<Primitive> primitives) {
        return validate(primitives).isEmpty();
    }
    
    // =========================================================================
    // Resolution
    // =========================================================================
    
    /**
     * Resolves radius from a linked primitive.
     * 
     * @param link The link configuration
     * @param primitiveIndex Index of primitives by ID
     * @return Resolved radius, or -1 if link is invalid
     */
    public static float resolveRadius(PrimitiveLink link, Map<String, Primitive> primitiveIndex) {
        if (link == null || !link.radiusMatch() || link.target() == null) {
            return -1;
        }
        
        Primitive target = primitiveIndex.get(link.target());
        if (target == null) {

            return -1;
        }
        
        // Get radius from target's shape
        float targetRadius = getShapeRadius(target.shape());
        if (targetRadius < 0) {

            return -1;
        }
        
        float result = targetRadius + link.radiusOffset();

        return result;
    }
    
    /**
     * Extracts radius from a shape.
     */
    private static float getShapeRadius(Shape shape) {
        if (shape instanceof SphereShape s) {
            return s.radius();
        } else if (shape instanceof RingShape r) {
            return r.outerRadius();
        } else if (shape instanceof CylinderShape c) {
            return c.radius();
        } else if (shape instanceof PrismShape p) {
            return p.radius();
        }
        return -1;
    }
    
    /**
     * Resolves a mirrored offset from the original offset.
     * 
     * <p>Mirroring inverts the offset on the specified axis:
     * <ul>
     *   <li>X: (-x, y, z)</li>
     *   <li>Y: (x, -y, z)</li>
     *   <li>Z: (x, y, -z)</li>
     * </ul>
     * 
     * @param link The link configuration
     * @param originalOffset The offset to mirror
     * @return Mirrored offset, or original if no mirror specified
     */
    public static Vector3f resolveMirror(PrimitiveLink link, Vector3f originalOffset) {
        if (link == null || link.mirror() == null) {
            return originalOffset;
        }
        
        Axis axis = link.mirror();
        Vector3f mirrored = new Vector3f(originalOffset);
        
        switch (axis) {
            case X -> mirrored.x = -mirrored.x;
            case Y -> mirrored.y = -mirrored.y;
            case Z -> mirrored.z = -mirrored.z;
        }

        return mirrored;
    }
    
    /**
     * Resolves position from a linked primitive (follow).
     * 
     * @param link The link configuration
     * @param primitiveIndex Index of primitives by ID
     * @return Target's offset, or null if link invalid
     */
    public static Vector3f resolveFollow(PrimitiveLink link, Map<String, Primitive> primitiveIndex) {
        if (link == null || !link.follow() || link.target() == null) {
            return null;
        }
        
        Primitive target = primitiveIndex.get(link.target());
        if (target == null) {

            return null;
        }
        
        Vector3f result = new Vector3f(target.transform().offset());

        return result;
    }
    
    /**
     * Resolves scale from a linked primitive (scaleWith).
     * 
     * @param link The link configuration
     * @param primitiveIndex Index of primitives by ID
     * @return Target's scale, or -1 if link invalid
     */
    public static float resolveScale(PrimitiveLink link, Map<String, Primitive> primitiveIndex) {
        if (link == null || !link.scaleWith() || link.target() == null) {
            return -1;
        }
        
        Primitive target = primitiveIndex.get(link.target());
        if (target == null) {

            return -1;
        }
        
        float result = target.transform().scale();

        return result;
    }
    
    /**
     * Resolves animation phase offset.
     * Simply returns the configured offset (0-1).
     * 
     * @param link The link configuration
     * @return Phase offset, or 0 if none
     */
    public static float resolvePhaseOffset(PrimitiveLink link) {
        if (link == null) {
            return 0;
        }
        return link.phaseOffset();
    }
    
    /**
     * Resolves orbit phase offset from a link.
     * 
     * @param link The link configuration
     * @return Orbit phase offset, or 0 if none
     */
    public static float resolveOrbitPhaseOffset(PrimitiveLink link) {
        if (link == null) {
            return 0;
        }
        return link.orbitPhaseOffset();
    }
    
    /**
     * Resolves orbit configuration from a link.
     * Returns the target primitive's orbit3d config if orbitSync is specified.
     * 
     * @param link The link configuration
     * @param primitiveIndex Index of primitives
     * @return Resolved OrbitConfig3D, or null if not linked
     */
    public static OrbitConfig3D resolveOrbit(PrimitiveLink link, Map<String, Primitive> primitiveIndex) {
        if (link == null || !link.orbitSync() || link.target() == null) {
            return null;
        }
        
        Primitive target = primitiveIndex.get(link.target());
        if (target == null) {

            return null;
        }
        
        Transform targetTransform = target.transform();
        if (targetTransform == null || !targetTransform.hasOrbit3D()) {

            return null;
        }
        
        return targetTransform.orbit3d();
    }
    
    /**
     * Resolves color from a linked primitive.
     * 
     * @param link The link configuration
     * @param primitiveIndex Index of primitives
     * @return Resolved color string, or null if not linked
     */
    public static String resolveColor(PrimitiveLink link, Map<String, Primitive> primitiveIndex) {
        if (link == null || !link.colorMatch() || link.target() == null) {
            return null;
        }
        
        Primitive target = primitiveIndex.get(link.target());
        if (target == null || target.appearance() == null) {
            return null;
        }
        
        return target.appearance().color();
    }
    
    /**
     * Resolves alpha from a linked primitive.
     * 
     * @param link The link configuration
     * @param primitiveIndex Index of primitives
     * @return Resolved alpha value (-1 if not linked)
     */
    public static float resolveAlpha(PrimitiveLink link, Map<String, Primitive> primitiveIndex) {
        if (link == null || !link.alphaMatch() || link.target() == null) {
            return -1;
        }
        
        Primitive target = primitiveIndex.get(link.target());
        if (target == null || target.appearance() == null || target.appearance().alpha() == null) {
            return -1;
        }
        
        return target.appearance().alpha().max();
    }
    
    // =========================================================================
    // Full Resolution (returns resolved VALUES)
    // =========================================================================
    
    /**
     * Container for resolved link values.
     * Used by builders/parsers to construct primitives with linked properties.
     * @param radius Resolved radius (-1 if not linked)
     * @param offset Resolved offset (null if not linked)
     * @param scale Resolved scale (-1 if not linked)
     * @param phaseOffset Animation phase offset (0 if not linked)
     * @param orbitConfig Resolved orbit config (null if not linked)
     * @param orbitPhaseOffset Orbit phase offset (0 if not linked)
     * @param orbitRadiusOffset Orbit radius offset (0 if not linked)
     * @param orbitSpeedMult Orbit speed multiplier (1 if not linked)
     * @param orbitInclinationOffset Inclination offset (0 if not linked)
     * @param orbitPrecessionOffset Precession offset (0 if not linked)
     * @param followDynamic Whether to follow target's animated position
     * @param color Resolved color (null if not linked)
     * @param alpha Resolved alpha (-1 if not linked)
     */
    public record ResolvedValues(
        float radius,
        Vector3f offset,
        float scale,
        float phaseOffset,
        OrbitConfig3D orbitConfig,
        float orbitPhaseOffset,
        float orbitRadiusOffset,
        float orbitSpeedMult,
        float orbitInclinationOffset,
        float orbitPrecessionOffset,
        boolean followDynamic,
        String color,
        float alpha
    ) {
        public static final ResolvedValues NONE = new ResolvedValues(
            -1, null, -1, 0, null, 0, 0, 1f, 0, 0, false, null, -1);
        
        public boolean hasRadius() { return radius >= 0; }
        public boolean hasOffset() { return offset != null; }
        public boolean hasScale() { return scale >= 0; }
        public boolean hasPhaseOffset() { return phaseOffset != 0; }
        public boolean hasOrbit() { return orbitConfig != null; }
        public boolean hasOrbitPhaseOffset() { return orbitPhaseOffset != 0; }
        public boolean hasOrbitRadiusOffset() { return orbitRadiusOffset != 0; }
        public boolean hasOrbitSpeedMult() { return orbitSpeedMult != 1f; }
        public boolean hasOrbitInclinationOffset() { return orbitInclinationOffset != 0; }
        public boolean hasOrbitPrecessionOffset() { return orbitPrecessionOffset != 0; }
        public boolean hasFollowDynamic() { return followDynamic; }
        public boolean hasColor() { return color != null; }
        public boolean hasAlpha() { return alpha >= 0; }
        public boolean hasAny() { 
            return hasRadius() || hasOffset() || hasScale() || hasPhaseOffset() 
                || hasOrbit() || hasOrbitPhaseOffset() || hasColor() || hasAlpha()
                || hasOrbitRadiusOffset() || hasOrbitSpeedMult() 
                || hasOrbitInclinationOffset() || hasOrbitPrecessionOffset() || hasFollowDynamic(); 
        }
    }
    
    /**
     * Resolves all link values for a primitive.
     * 
     * <p>Returns resolved VALUES that can be used when building/modifying primitives.
     * Does not modify the original primitive (which may be immutable).
     * 
     * <p>Resolution order:
     * <ol>
     *   <li>Radius matching (radiusMatch + radiusOffset)</li>
     *   <li>Position follow + mirror</li>
     *   <li>Scale synchronization</li>
     *   <li>Phase offset</li>
     *   <li>Orbit synchronization + orbit phase offset</li>
     *   <li>Color/alpha matching</li>
     * </ol>
     * 
     * @param primitive The primitive to resolve links for
     * @param primitiveIndex Index of available primitives (only prior ones)
     * @return ResolvedValues with any linked properties
     */
    public static ResolvedValues resolveLinks(Primitive primitive, Map<String, Primitive> primitiveIndex) {
        PrimitiveLink link = primitive.link();
        if (link == null || !link.isValid() || !link.hasAnyLinkType()) {
            return ResolvedValues.NONE;
        }
        
        // 1. Radius matching
        float resolvedRadius = resolveRadius(link, primitiveIndex);
        
        // 2. Follow position (static only - dynamic follow is handled in renderer)
        Vector3f resolvedOffset = resolveFollow(link, primitiveIndex);
        
        // 3. Mirror (applied to resolved offset, or original if no follow)
        if (link.mirror() != null) {
            Vector3f baseOffset = resolvedOffset != null ? resolvedOffset : 
                new Vector3f(primitive.transform().offset());
            resolvedOffset = resolveMirror(link, baseOffset);
        }
        
        // 4. Scale synchronization
        float resolvedScale = resolveScale(link, primitiveIndex);
        
        // 5. Phase offset
        float phaseOffset = resolvePhaseOffset(link);
        
        // 6. Orbit synchronization
        OrbitConfig3D orbitConfig = resolveOrbit(link, primitiveIndex);
        float orbitPhaseOffset = resolveOrbitPhaseOffset(link);
        
        // 7. NEW: Orbit parameter offsets
        float orbitRadiusOffset = link.orbitRadiusOffset();
        float orbitSpeedMult = link.orbitSpeedMult();
        float orbitInclinationOffset = link.orbitInclinationOffset();
        float orbitPrecessionOffset = link.orbitPrecessionOffset();
        boolean followDynamic = link.followDynamic();
        
        // 8. Color/alpha matching
        String color = resolveColor(link, primitiveIndex);
        float alpha = resolveAlpha(link, primitiveIndex);
        
        ResolvedValues result = new ResolvedValues(
            resolvedRadius, resolvedOffset, resolvedScale, phaseOffset,
            orbitConfig, orbitPhaseOffset, orbitRadiusOffset, orbitSpeedMult,
            orbitInclinationOffset, orbitPrecessionOffset, followDynamic, color, alpha);

        return result;
    }
    
    /**
     * Applies resolved link values to create a new Transform.
     * 
     * @param original Original transform
     * @param resolved Resolved link values
     * @return New transform with resolved values applied
     */
    public static Transform applyToTransform(Transform original, ResolvedValues resolved) {
        Transform result = original;
        if (resolved.hasOffset()) {
            result = result.withOffset(resolved.offset());
        }
        if (resolved.hasScale()) {
            result = result.withScale(resolved.scale());
        }
        return result;
    }
    
    /**
     * Builds an index of primitives by ID.
     * Only includes primitives up to the given index (for forward ref prevention).
     */
    public static Map<String, Primitive> buildIndex(List<Primitive> primitives, int upToIndex) {
        Map<String, Primitive> index = new HashMap<>();
        for (int i = 0; i <= upToIndex && i < primitives.size(); i++) {
            Primitive p = primitives.get(i);
            if (p.id() != null) {
                index.put(p.id(), p);
            }
        }
        return index;
    }
    
    /**
     * Builds a complete index of all primitives.
     */
    public static Map<String, Primitive> buildIndex(List<Primitive> primitives) {
        return buildIndex(primitives, primitives.size() - 1);
    }
}
