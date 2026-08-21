package net.cyberpunk042.mcshaders.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or member as public but not yet stable.
 *
 * <p>Experimental API is offered so it can be used and criticised early. It may
 * change or be removed in any release. Depend on it when the feedback is worth the
 * churn; pin your version if it is not.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Experimental {
    /** Why this is still experimental, so callers can judge the risk. */
    String reason() default "";
}
