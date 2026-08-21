/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 */
package net.cyberpunk042.mcshaders.core.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the valid range for a numeric field or parameter.
 * 
 * <p>This annotation serves multiple purposes:</p>
 * <ul>
 *   <li><b>Documentation:</b> Clearly indicates valid values in Javadoc</li>
 *   <li><b>IDE Support:</b> Enables autocomplete for range types</li>
 *   <li><b>Validation:</b> Can be used with {@link ValueRange#clamp} for runtime validation</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>On Record Components</h3>
 * <pre>
 * public record AlphaRange(
 *     {@literal @}Range(ValueRange.ALPHA) float min,
 *     {@literal @}Range(ValueRange.ALPHA) float max
 * ) {
 *     public AlphaRange {
 *         min = ValueRange.ALPHA.clamp(min);
 *         max = ValueRange.ALPHA.clamp(max);
 *     }
 * }
 * </pre>
 * 
 * <h3>On Method Parameters</h3>
 * <pre>
 * public void setRadius({@literal @}Range(ValueRange.RADIUS) float radius) {
 *     this.radius = ValueRange.RADIUS.clamp(radius);
 * }
 * </pre>
 * 
 * <h3>On Fields</h3>
 * <pre>
 * {@literal @}Range(ValueRange.DEGREES)
 * private float rotation;
 * </pre>
 * 
 * @see ValueRange
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.FIELD,
    ElementType.PARAMETER,
    ElementType.RECORD_COMPONENT,
    ElementType.METHOD,
    ElementType.LOCAL_VARIABLE
})
public @interface Range {
    
    /**
     * The valid range for this value.
     * 
     * @return the ValueRange enum constant defining valid bounds
     */
    ValueRange value();
}

