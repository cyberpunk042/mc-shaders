package net.cyberpunk042.mcshaders.core.transition;

import net.cyberpunk042.mcshaders.core.param.Interpolation;

/** Easing curves for transitions. Every curve maps {@code [0,1] -> [0,1]}. */
public enum Easing {

    LINEAR {
        @Override
        double curve(double t) {
            return t;
        }
    },

    /** Smoothstep: eases both ends, the general-purpose default. */
    SMOOTH {
        @Override
        double curve(double t) {
            return t * t * (3.0 - 2.0 * t);
        }
    },

    /** Slow start, fast finish. */
    EASE_IN {
        @Override
        double curve(double t) {
            return t * t;
        }
    },

    /** Fast start, slow finish. */
    EASE_OUT {
        @Override
        double curve(double t) {
            return 1.0 - (1.0 - t) * (1.0 - t);
        }
    },

    /** Cubic ease at both ends; more pronounced than {@link #SMOOTH}. */
    EASE_IN_OUT {
        @Override
        double curve(double t) {
            return t < 0.5
                    ? 4.0 * t * t * t
                    : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
        }
    };

    abstract double curve(double t);

    /** Applies the curve with the input clamped to {@code [0, 1]}. */
    public double apply(double t) {
        return curve(Interpolation.clamp01(t));
    }
}
