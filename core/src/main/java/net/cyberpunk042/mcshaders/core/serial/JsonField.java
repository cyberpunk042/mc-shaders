package net.cyberpunk042.mcshaders.core.serial;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for configuring JSON serialization/deserialization behavior.
 * 
 * <p>Use cases:</p>
 * <ul>
 *   <li><b>aliases</b> - Accept alternative JSON keys when deserializing
 *       (e.g., fragment files use short names like "latSteps" that map to "sphereLatSteps")</li>
 *   <li><b>exclude</b> - Skip this field during serialization (like transient, but explicit)</li>
 *   <li><b>name</b> - Use a different JSON key name than the field name</li>
 * </ul>
 * 
 * <h3>Example: Fragment alias mapping</h3>
 * <pre>{@code
 * // Field in FieldEditState
 * @JsonField(aliases = {"latSteps"})
 * private int sphereLatSteps = 32;
 * 
 * // When loading sphere fragment JSON: {"latSteps": 64}
 * // JsonSerializer will map "latSteps" → sphereLatSteps field
 * }</pre>
 * 
 * <h3>Example: Exclude from serialization</h3>
 * <pre>{@code
 * @JsonField(exclude = true)
 * private transient String snapshotJson;  // Not saved in JSON
 * }</pre>
 * 
 * @see JsonSerializer
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonField {
    
    /**
     * The primary JSON key name. If empty, uses the field name.
     * 
     * <p>Example: {@code @JsonField(name = "lat_steps")} on field {@code latSteps}
     * will serialize as "lat_steps" instead of "latSteps".</p>
     */
    String name() default "";
    
    /**
     * Alternative JSON keys that should also map to this field during deserialization.
     * 
     * <p>This is useful for:</p>
     * <ul>
     *   <li>Fragment files that use short names (e.g., "latSteps" → sphereLatSteps)</li>
     *   <li>Backward compatibility when renaming fields</li>
     *   <li>Supporting multiple JSON formats</li>
     * </ul>
     * 
     * <p>Example: {@code @JsonField(aliases = {"latSteps", "lat_steps"})}
     * accepts both "latSteps" and "lat_steps" as valid JSON keys.</p>
     */
    String[] aliases() default {};
    
    /**
     * If true, this field is excluded from JSON serialization/deserialization.
     * 
     * <p>Equivalent to marking a field as transient, but more explicit
     * and works with non-transient fields.</p>
     */
    boolean exclude() default false;
    
    /**
     * Optional group/category for the field.
     * 
     * <p>Used by applicators to apply only certain categories of fields.
     * For example, FragmentRegistry might only apply fields in group "sphere"
     * when loading a sphere preset.</p>
     * 
     * <p>Example: {@code @JsonField(group = "sphere")} on sphereLatSteps</p>
     */
    String group() default "";
    
    /**
     * If true, skip serialization when the field equals its default value.
     * 
     * <p>Default values are determined by:</p>
     * <ul>
     *   <li>Primitives: 0, 0.0, false, '\0'</li>
     *   <li>Objects: null</li>
     *   <li>Or use {@link #defaultValue()} to specify a custom default</li>
     * </ul>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * @JsonField(skipIfDefault = true)
     * private boolean oscillate = false;  // Not serialized when false
     * 
     * @JsonField(skipIfDefault = true, defaultValue = "360")
     * private float range = 360f;  // Not serialized when 360
     * }</pre>
     */
    boolean skipIfDefault() default false;
    
    /**
     * Custom default value for {@link #skipIfDefault()}.
     * 
     * <p>The string is parsed according to the field type:</p>
     * <ul>
     *   <li>int/Integer: "0", "100"</li>
     *   <li>float/Float: "0.0", "360.0"</li>
     *   <li>boolean/Boolean: "true", "false"</li>
     *   <li>String: the literal value</li>
     *   <li>Enum: the enum constant name</li>
     * </ul>
     * 
     * <p>Example: {@code @JsonField(skipIfDefault = true, defaultValue = "360")}
     * skips serialization when the float field equals 360.</p>
     */
    String defaultValue() default "";
    
    /**
     * If true, skip serialization when the field is null.
     * 
     * <p>This is a simpler alternative to {@code skipIfDefault = true} for
     * nullable object fields.</p>
     */
    boolean skipIfNull() default false;
    
    /**
     * If true, skip serialization when the field is null or empty.
     * 
     * <p>Works with:</p>
     * <ul>
     *   <li>Collections (List, Set, Map) - skips if null or isEmpty()</li>
     *   <li>Strings - skips if null or isEmpty()</li>
     *   <li>Arrays - skips if null or length == 0</li>
     * </ul>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * @JsonField(skipIfEmpty = true)
     * List<String> colors;  // Not serialized when null or empty
     * }</pre>
     */
    boolean skipIfEmpty() default false;
    
    /**
     * Skip serialization when this field equals another field's value.
     * 
     * <p>Useful when a field defaults to matching another field, e.g.,
     * {@code topRadius} defaults to equal {@code radius}.</p>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * float radius;
     * 
     * @JsonField(skipIfEqualsField = "radius")
     * float topRadius;  // Not serialized when topRadius == radius
     * }</pre>
     */
    String skipIfEqualsField() default "";
    
    /**
     * Only serialize if the specified method on the field value returns true.
     * 
     * <p>The method must be a no-argument method on the field's type that returns boolean.
     * If the field is null or the method returns false, serialization is skipped.</p>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * @JsonField(skipUnless = "isActive")
     * SpinConfig spin;  // Only serialized if spin != null && spin.isActive()
     * }</pre>
     */
    String skipUnless() default "";
    
    /**
     * Skip serialization when this field equals a static constant of the same type.
     * 
     * <p>The constant is looked up as a static field on the field's declared type.
     * Useful when a field defaults to a "none" or "identity" constant.</p>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * @JsonField(skipIfEqualsConstant = "NONE")
     * DecayConfig decay;  // Not serialized when decay.equals(DecayConfig.NONE)
     * 
     * @JsonField(skipIfEqualsConstant = "IDENTITY")
     * Transform transform;  // Not serialized when transform.equals(Transform.IDENTITY)
     * }</pre>
     */
    String skipIfEqualsConstant() default "";
}

