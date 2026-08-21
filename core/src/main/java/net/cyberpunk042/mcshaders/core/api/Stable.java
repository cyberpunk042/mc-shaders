package net.cyberpunk042.mcshaders.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or member as part of the supported public API.
 *
 * <p>Stable API does not break within a major version: signatures are not removed
 * or changed incompatibly, and behaviour changes only in ways existing callers can
 * absorb. Anything not marked {@code @Stable} or {@link Experimental} is internal
 * and may change without notice, even if it happens to be public for technical
 * reasons.
 *
 * <p>Declared here rather than pulled from an annotations library so the core stays
 * dependency-free — see the module's build script.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Stable {
    /** The version this became stable, for changelog and deprecation tracking. */
    String since() default "";
}
