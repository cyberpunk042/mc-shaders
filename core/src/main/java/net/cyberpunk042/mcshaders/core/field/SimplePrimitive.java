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

import net.cyberpunk042.mcshaders.core.field.Primitive;
import net.cyberpunk042.mcshaders.core.field.PrimitiveLink;
import net.cyberpunk042.mcshaders.core.animation.Animation;
import net.cyberpunk042.mcshaders.core.appearance.Appearance;
import net.cyberpunk042.mcshaders.core.fill.FillConfig;
import net.cyberpunk042.mcshaders.core.pattern.ArrangementConfig;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.transform.Transform;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Record implementation of {@link Primitive}.
 *
 * <p>Primitives are immutable; the {@code with*} methods return copies. A codec
 * layer above core is what binds this to JSON — core itself does no parsing.</p>
 *
 * @see Primitive
 */
public record SimplePrimitive(
    String id,
    String type,
    @JsonField(skipIfNull = true) Shape shape,
    @JsonField(skipIfEqualsConstant = "Transform.IDENTITY", skipIfNull = true) Transform transform,
    @JsonField(skipIfNull = true) FillConfig fill,
    @JsonField(skipIfEqualsConstant = "VisibilityMask.FULL", skipIfNull = true) VisibilityMask visibility,
    @JsonField(skipIfEqualsConstant = "ArrangementConfig.DEFAULT", skipIfNull = true) ArrangementConfig arrangement,
    @JsonField(skipIfEqualsConstant = "Appearance.DEFAULT", skipIfNull = true) Appearance appearance,
    @JsonField(skipIfEqualsConstant = "Animation.NONE", skipIfNull = true) Animation animation,
    @JsonField(skipIfEqualsConstant = "PrimitiveLink.NONE", skipIfNull = true) PrimitiveLink link
)implements Primitive {
    
    /**
     * Creates a SimplePrimitive with default values for optional fields.
     */
    public static SimplePrimitive of(String id, String type, Shape shape) {
        return new SimplePrimitive(
            id, type, shape,
            Transform.IDENTITY,
            FillConfig.SOLID,
            VisibilityMask.FULL,
            ArrangementConfig.DEFAULT,
            Appearance.DEFAULT,
            Animation.NONE,
            PrimitiveLink.NONE
        );
    }
    
    /**
     * Returns a copy with a different shape.
     */
    public SimplePrimitive withShape(Shape newShape) {
        return new SimplePrimitive(id, type, newShape, transform, fill, visibility,
            arrangement, appearance, animation, link);
    }
    
    /**
     * Returns a copy with a different transform.
     */
    public SimplePrimitive withTransform(Transform newTransform) {
        return new SimplePrimitive(id, type, shape, newTransform, fill, visibility,
            arrangement, appearance, animation, link);
    }
    
    /**
     * Returns a copy with a different appearance.
     */
    public SimplePrimitive withAppearance(Appearance newAppearance) {
        return new SimplePrimitive(id, type, shape, transform, fill, visibility,
            arrangement, newAppearance, animation, link);
    }
    
    /**
     * Returns a copy with a different link.
     */
    public SimplePrimitive withLink(PrimitiveLink newLink) {
        return new SimplePrimitive(id, type, shape, transform, fill, visibility,
            arrangement, appearance, animation, newLink);
    }
    
    /**
     * Returns a copy with a different animation.
     */
    public SimplePrimitive withAnimation(Animation newAnimation) {
        return new SimplePrimitive(id, type, shape, transform, fill, visibility,
            arrangement, appearance, newAnimation, link);
    }
    
}
