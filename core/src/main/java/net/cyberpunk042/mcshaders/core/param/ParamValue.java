package net.cyberpunk042.mcshaders.core.param;

/**
 * A single shader parameter value.
 *
 * <p>Values are immutable and interpolable. Interpolation is what makes smooth
 * dimension transitions possible: crossing a portal blends the source stack into
 * the destination stack over time rather than snapping between them.
 *
 * <p>Interpolating two values of different shapes is not meaningful (there is no
 * sensible midpoint between a colour and a boolean), so {@link #lerp} falls back
 * to a step at the halfway mark in that case rather than throwing. A malformed
 * pack should degrade visually, not crash the client.
 */
public sealed interface ParamValue
        permits ParamValue.Scalar, ParamValue.Vec3, ParamValue.Rgba, ParamValue.Flag, ParamValue.Text {

    /** A single number: intensity, radius, exposure. */
    record Scalar(double value) implements ParamValue {
        @Override
        public ParamValue lerp(ParamValue other, double t) {
            if (other instanceof Scalar s) {
                return new Scalar(Interpolation.mix(value, s.value, t));
            }
            return Interpolation.step(this, other, t);
        }
    }

    /** A 3-component vector: direction, offset, axis weights. */
    record Vec3(double x, double y, double z) implements ParamValue {
        @Override
        public ParamValue lerp(ParamValue other, double t) {
            if (other instanceof Vec3 v) {
                return new Vec3(
                        Interpolation.mix(x, v.x, t),
                        Interpolation.mix(y, v.y, t),
                        Interpolation.mix(z, v.z, t));
            }
            return Interpolation.step(this, other, t);
        }
    }

    /**
     * A linear (not sRGB-encoded) colour with alpha.
     *
     * <p>Components are clamped to {@code [0, 1]} on construction. Blending is done
     * component-wise in linear space, which is the correct space for mixing light;
     * conversion to and from sRGB belongs in the rendering backend, not here.
     */
    record Rgba(float r, float g, float b, float a) implements ParamValue {
        public Rgba {
            r = clamp01(r);
            g = clamp01(g);
            b = clamp01(b);
            a = clamp01(a);
        }

        public static Rgba opaque(float r, float g, float b) {
            return new Rgba(r, g, b, 1.0f);
        }

        private static float clamp01(float v) {
            if (Float.isNaN(v)) {
                return 0.0f;
            }
            return Math.max(0.0f, Math.min(1.0f, v));
        }

        @Override
        public ParamValue lerp(ParamValue other, double t) {
            if (other instanceof Rgba c) {
                return new Rgba(
                        (float) Interpolation.mix(r, c.r, t),
                        (float) Interpolation.mix(g, c.g, t),
                        (float) Interpolation.mix(b, c.b, t),
                        (float) Interpolation.mix(a, c.a, t));
            }
            return Interpolation.step(this, other, t);
        }
    }

    /** A boolean toggle. Steps at the halfway point rather than blending. */
    record Flag(boolean value) implements ParamValue {
        @Override
        public ParamValue lerp(ParamValue other, double t) {
            return Interpolation.step(this, other, t);
        }
    }

    /** An opaque string, e.g. the id of a texture or a named sub-mode. */
    record Text(String value) implements ParamValue {
        public Text {
            if (value == null) {
                throw new IllegalArgumentException("Text parameter value must not be null");
            }
        }

        @Override
        public ParamValue lerp(ParamValue other, double t) {
            return Interpolation.step(this, other, t);
        }
    }

    /**
     * Blends this value toward {@code other}.
     *
     * @param t progress in {@code [0, 1]}; values outside are clamped
     * @return the blended value, or a stepped one for non-interpolable shapes
     */
    ParamValue lerp(ParamValue other, double t);

    static Scalar of(double value) {
        return new Scalar(value);
    }

    static Flag of(boolean value) {
        return new Flag(value);
    }

    static Text of(String value) {
        return new Text(value);
    }
}
