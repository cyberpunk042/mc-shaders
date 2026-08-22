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

import net.cyberpunk042.mcshaders.core.animation.Animation;
import net.cyberpunk042.mcshaders.core.appearance.Appearance;
import net.cyberpunk042.mcshaders.core.fill.FillConfig;
import net.cyberpunk042.mcshaders.core.pattern.ArrangementConfig;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.transform.Transform;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;

/**
 * A renderable shape with complete configuration.
 * 
 * <p>Primitives combine:</p>
 * <ul>
 *   <li><b>Shape</b> - Geometry (sphere, ring, disc, etc.)</li>
 *   <li><b>Transform</b> - Position, rotation, scale</li>
 *   <li><b>Fill</b> - How surface is rendered (solid, wireframe, cage)</li>
 *   <li><b>Visibility</b> - Masking (bands, stripes, etc.)</li>
 *   <li><b>Arrangement</b> - Vertex patterns per part</li>
 *   <li><b>Appearance</b> - Color, alpha, glow</li>
 *   <li><b>Animation</b> - Spin, pulse, phase</li>
 *   <li><b>Link</b> - Connections to other primitives</li>
 * </ul>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * {
 *   "id": "main_sphere",
 *   "type": "sphere",
 *   "shape": { "radius": 1.0 },
 *   "transform": { "anchor": "CENTER" },
 *   "fill": { "mode": "SOLID" },
 *   "visibility": { "mask": "FULL" },
 *   "arrangement": "filled_1",
 *   "appearance": { "color": "@primary" },
 *   "animation": { "spin": 0.02 },
 *   "link": null
 * }
 * </pre>
 * 
 * @see Shape
 * @see Transform
 * @see Appearance
 * @see Animation
 * @see FillConfig
 * @see VisibilityMask
 */
public interface Primitive {
    
    /**
     * Unique identifier for this primitive within the field.
     * <p>REQUIRED - used for linking, debugging, and references.</p>
     * @return Primitive ID
     */
    String id();
    
    /**
     * Shape type name.
     * @return Type (e.g., "sphere", "ring", "disc")
     */
    String type();
    
    /**
     * The geometric shape.
     * @return Shape configuration
     */
    Shape shape();
    
    /**
     * Transform configuration.
     * @return Transform (position, rotation, scale)
     */
    Transform transform();
    
    /**
     * Fill configuration.
     * @return Fill mode and options
     */
    FillConfig fill();
    
    /**
     * Visibility mask configuration.
     * @return Visibility mask
     */
    VisibilityMask visibility();
    
    /**
     * Arrangement configuration.
     * @return Arrangement patterns per part
     */
    ArrangementConfig arrangement();
    
    /**
     * Visual appearance.
     * @return Appearance (color, alpha, glow, etc.)
     */
    Appearance appearance();
    
    /**
     * Animation configuration.
     * @return Animation (spin, pulse, etc.)
     */
    Animation animation();
    
    /**
     * Link to other primitives.
     * @return Link configuration, or null if not linked
     */
    PrimitiveLink link();
    
    // =========================================================================
    // Convenience Methods
    // =========================================================================
    
    /**
     * Whether this primitive has active animation.
     */
    default boolean isAnimated() {
        Animation anim = animation();
        return anim != null && anim.isActive();
    }
    
    /**
     * Whether this primitive is linked to others.
     */
    default boolean isLinked() {
        return link() != null;
    }
    
    /**
     * Gets the approximate radius of this primitive.
     */
    default float getRadius() {
        return shape().getRadius();
    }
}
