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

import org.joml.Vector3f;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Stack of transforms for hierarchical rendering.
 * 
 * <p>Allows pushing and popping transforms like a matrix stack,
 * with accumulated effects.</p>
 * 
 * @see Transform
 */
public class TransformStack {
    
    private final Deque<Transform> stack = new ArrayDeque<>();
    
    public TransformStack() {
        stack.push(Transform.IDENTITY);
    }
    
    /**
     * Pushes a copy of the current transform onto the stack.
     */
    public void push() {
        stack.push(peek());
    }
    
    /**
     * Pops the top transform from the stack.
     * @throws IllegalStateException if stack would become empty
     */
    public void pop() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("Cannot pop the last transform from stack");
        }
        stack.pop();
    }
    
    /**
     * Returns the current (top) transform without removing it.
     */
    public Transform peek() {
        return stack.peek();
    }
    
    /**
     * Applies a transform to the current state.
     * @param transform Transform to combine with current
     */
    public void apply(Transform transform) {
        if (transform == null || transform == Transform.IDENTITY) return;
        
        Transform current = stack.pop();
        stack.push(combine(current, transform));
    }
    
    /**
     * Translates the current transform.
     * @param x X offset
     * @param y Y offset
     * @param z Z offset
     */
    public void translate(float x, float y, float z) {
        Transform current = stack.pop();
        Vector3f newOffset = current.offset() != null ? 
            new Vector3f(current.offset()).add(x, y, z) : 
            new Vector3f(x, y, z);
        stack.push(Transform.builder()
            .anchor(current.anchor())
            .offset(newOffset)
            .rotation(current.rotation())
            .inheritRotation(current.inheritRotation())
            .scale(current.scale())
            .scaleXYZ(current.scaleXYZ())
            .scaleWithRadius(current.scaleWithRadius())
            .facing(current.facing())
            .up(current.up())
            .billboard(current.billboard())
            .orbit(current.orbit())
            .orbit3d(current.orbit3d())
            .build());
    }
    
    /**
     * Scales the current transform.
     * @param scale Uniform scale factor
     */
    public void scale(float scale) {
        Transform current = stack.pop();
        stack.push(Transform.builder()
            .anchor(current.anchor())
            .offset(current.offset())
            .rotation(current.rotation())
            .inheritRotation(current.inheritRotation())
            .scale(current.scale() * scale)
            .scaleXYZ(current.scaleXYZ() != null ? 
                new Vector3f(current.scaleXYZ()).mul(scale) : null)
            .scaleWithRadius(current.scaleWithRadius())
            .facing(current.facing())
            .up(current.up())
            .billboard(current.billboard())
            .orbit(current.orbit())
            .orbit3d(current.orbit3d())
            .build());
    }
    
    /**
     * Rotates the current transform.
     * @param pitch X rotation in degrees
     * @param yaw Y rotation in degrees
     * @param roll Z rotation in degrees
     */
    public void rotate(float pitch, float yaw, float roll) {
        Transform current = stack.pop();
        Vector3f newRot = current.rotation() != null ?
            new Vector3f(current.rotation()).add(pitch, yaw, roll) :
            new Vector3f(pitch, yaw, roll);
        stack.push(Transform.builder()
            .anchor(current.anchor())
            .offset(current.offset())
            .rotation(newRot)
            .inheritRotation(current.inheritRotation())
            .scale(current.scale())
            .scaleXYZ(current.scaleXYZ())
            .scaleWithRadius(current.scaleWithRadius())
            .facing(current.facing())
            .up(current.up())
            .billboard(current.billboard())
            .orbit(current.orbit())
            .orbit3d(current.orbit3d())
            .build());
    }
    
    /**
     * Resets the stack to identity transform.
     */
    public void reset() {
        stack.clear();
        stack.push(Transform.IDENTITY);
    }
    
    /**
     * Returns the stack depth.
     */
    public int depth() {
        return stack.size();
    }
    
    /**
     * Combines two transforms.
     * @param base Base transform
     * @param applied Transform to apply on top
     * @return Combined transform
     */
    private static Transform combine(Transform base, Transform applied) {
        // Combine offsets
        Vector3f offset = null;
        if (base.offset() != null || applied.offset() != null) {
            offset = new Vector3f();
            if (base.offset() != null) offset.add(base.offset());
            if (applied.offset() != null) offset.add(applied.offset());
        }
        
        // Combine rotations
        Vector3f rotation = null;
        if (base.rotation() != null || applied.rotation() != null) {
            rotation = new Vector3f();
            if (base.rotation() != null) rotation.add(base.rotation());
            if (applied.rotation() != null) rotation.add(applied.rotation());
        }
        
        // Multiply scales
        float scale = base.scale() * applied.scale();
        
        // Combine scaleXYZ
        Vector3f scaleXYZ = null;
        if (base.scaleXYZ() != null || applied.scaleXYZ() != null) {
            Vector3f bScale = base.scaleXYZ() != null ? base.scaleXYZ() : new Vector3f(base.scale());
            Vector3f aScale = applied.scaleXYZ() != null ? applied.scaleXYZ() : new Vector3f(applied.scale());
            scaleXYZ = new Vector3f(bScale).mul(aScale);
        }
        
        // Applied transform wins for non-additive properties
        return new Transform(
            applied.anchor() != Anchor.CENTER ? applied.anchor() : base.anchor(),
            offset,
            rotation,
            applied.inheritRotation() && base.inheritRotation(),
            scale,
            scaleXYZ,
            applied.scaleWithRadius() || base.scaleWithRadius(),
            applied.facing() != Facing.FIXED ? applied.facing() : base.facing(),
            applied.up() != UpVector.WORLD_UP ? applied.up() : base.up(),
            applied.billboard() != Billboard.NONE ? applied.billboard() : base.billboard(),
            applied.orbit() != null ? applied.orbit() : base.orbit(),
            applied.orbit3d() != null ? applied.orbit3d() : base.orbit3d()
        );
    }
}
