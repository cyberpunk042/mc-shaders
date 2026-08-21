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
package net.cyberpunk042.mcshaders.core.animation;

/**
 * Easing functions for smooth animation transitions.
 * 
 * <p>Used by {@link ShapeModifier} and stage transitions to control
 * how values interpolate over time.</p>
 * 
 * <h2>Usage</h2>
 * <pre>
 * float t = 0.5f; // Progress 0-1
 * float eased = EaseFunction.EASE_OUT_QUAD.apply(t);
 * </pre>
 * 
 * @see ShapeModifier
 * @see StageConfig
 */
public enum EaseFunction {
    /** Linear interpolation (no easing). */
    LINEAR {
        @Override public float apply(float t) { return t; }
    },
    
    /** Slow start, accelerating. */
    EASE_IN_QUAD {
        @Override public float apply(float t) { return t * t; }
    },
    
    /** Fast start, decelerating. */
    EASE_OUT_QUAD {
        @Override public float apply(float t) { return t * (2 - t); }
    },
    
    /** Slow start and end. */
    EASE_IN_OUT_QUAD {
        @Override public float apply(float t) {
            return t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t;
        }
    },
    
    /** Cubic ease in. */
    EASE_IN_CUBIC {
        @Override public float apply(float t) { return t * t * t; }
    },
    
    /** Cubic ease out. */
    EASE_OUT_CUBIC {
        @Override public float apply(float t) {
            float t1 = t - 1;
            return t1 * t1 * t1 + 1;
        }
    },
    
    /** Cubic ease in/out. */
    EASE_IN_OUT_CUBIC {
        @Override public float apply(float t) {
            return t < 0.5f ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
        }
    },
    
    /** Exponential ease out (very fast start). */
    EASE_OUT_EXPO {
        @Override public float apply(float t) {
            return t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
        }
    },
    
    /** Exponential ease in (very slow start). */
    EASE_IN_EXPO {
        @Override public float apply(float t) {
            return t == 0 ? 0 : (float) Math.pow(2, 10 * (t - 1));
        }
    },
    
    /** Overshoot then settle (spring effect). */
    EASE_OUT_BACK {
        @Override public float apply(float t) {
            float c1 = 1.70158f;
            float c3 = c1 + 1;
            return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
        }
    },
    
    /** Bounce effect at end. */
    EASE_OUT_BOUNCE {
        @Override public float apply(float t) {
            float n1 = 7.5625f;
            float d1 = 2.75f;
            if (t < 1 / d1) {
                return n1 * t * t;
            } else if (t < 2 / d1) {
                t -= 1.5f / d1;
                return n1 * t * t + 0.75f;
            } else if (t < 2.5 / d1) {
                t -= 2.25f / d1;
                return n1 * t * t + 0.9375f;
            } else {
                t -= 2.625f / d1;
                return n1 * t * t + 0.984375f;
            }
        }
    },
    
    /** Elastic spring effect. */
    EASE_OUT_ELASTIC {
        @Override public float apply(float t) {
            if (t == 0 || t == 1) return t;
            float p = 0.3f;
            return (float) (Math.pow(2, -10 * t) * Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1);
        }
    };
    
    /**
     * Apply this easing function to a progress value.
     * 
     * @param t Progress from 0 to 1
     * @return Eased value (may exceed 0-1 range for overshoot eases)
     */
    public abstract float apply(float t);
    
    /**
     * Interpolate between two values using this easing function.
     * 
     * @param start Start value
     * @param end End value
     * @param t Progress from 0 to 1
     * @return Interpolated value
     */
    public float lerp(float start, float end, float t) {
        return start + (end - start) * apply(t);
    }
    
    /**
     * Parse from string, case-insensitive.
     * Supports formats: "EASE_OUT_QUAD", "easeOutQuad", "ease-out-quad"
     */
    public static EaseFunction fromString(String value) {
        if (value == null || value.isEmpty()) return LINEAR;
        try {
            // Normalize: convert camelCase or kebab-case to UPPER_SNAKE_CASE
            String normalized = value
                .replaceAll("([a-z])([A-Z])", "$1_$2")  // camelCase to snake
                .replace("-", "_")                       // kebab to snake
                .toUpperCase();
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return LINEAR;
        }
    }
}
