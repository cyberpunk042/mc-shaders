package net.cyberpunk042.mcshaders.core.binding;

import java.util.List;

/**
 * A predicate over {@link WorldState} deciding whether a binding is active.
 *
 * <p>Conditions are a closed, data-shaped algebra rather than arbitrary lambdas so
 * that a datapack can express them declaratively and the framework can serialise,
 * diff, and reason about them.
 */
public sealed interface Condition {

    boolean test(WorldState state);

    /** Always active. */
    record Always() implements Condition {
        @Override
        public boolean test(WorldState state) {
            return true;
        }
    }

    /** Never active. Useful for temporarily disabling a binding without deleting it. */
    record Never() implements Condition {
        @Override
        public boolean test(WorldState state) {
            return false;
        }
    }

    /**
     * Active between two times of day, in ticks.
     *
     * <p>Ranges wrap: {@code from=13000, to=1000} covers night, spanning midnight.
     * Without wrapping, every night-time condition would need to be expressed as
     * two rules.
     */
    record TimeOfDay(long from, long to) implements Condition {
        public TimeOfDay {
            from = Math.floorMod(from, WorldState.DAY_LENGTH_TICKS);
            to = Math.floorMod(to, WorldState.DAY_LENGTH_TICKS);
        }

        @Override
        public boolean test(WorldState state) {
            if (!state.hasDayTime()) {
                // The clock cannot be read on this version. False rather than a guess:
                // a time-gated look that is reliably off is a thing an author can
                // notice and ask about, and SamplingGaps names the binding for them.
                return false;
            }
            long t = state.dayTime();
            if (from <= to) {
                return t >= from && t < to;
            }
            return t >= from || t < to;
        }
    }

    /** Active within a vertical band. Bounds are inclusive. */
    record YRange(double min, double max) implements Condition {
        public YRange {
            if (min > max) {
                double swap = min;
                min = max;
                max = swap;
            }
        }

        @Override
        public boolean test(WorldState state) {
            return state.yLevel() >= min && state.yLevel() <= max;
        }
    }

    /** Active in the given weather. */
    record InWeather(Weather weather) implements Condition {
        public InWeather {
            if (weather == null) {
                throw new IllegalArgumentException("InWeather requires a weather value");
            }
        }

        @Override
        public boolean test(WorldState state) {
            return state.weather() == weather;
        }
    }

    /** Active when the viewer's biome carries the given tag. */
    record HasBiomeTag(String tag) implements Condition {
        public HasBiomeTag {
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException("HasBiomeTag requires a tag");
            }
            tag = tag.startsWith("#") ? tag.substring(1) : tag;
        }

        @Override
        public boolean test(WorldState state) {
            return state.biomeTags().contains(tag);
        }
    }

    /** Active when the view is submerged in a fluid. */
    record Submerged() implements Condition {
        @Override
        public boolean test(WorldState state) {
            return state.submerged();
        }
    }

    /** Active only when every child is. An empty list is vacuously true. */
    record All(List<Condition> children) implements Condition {
        public All {
            children = List.copyOf(children);
        }

        @Override
        public boolean test(WorldState state) {
            return children.stream().allMatch(c -> c.test(state));
        }
    }

    /** Active when any child is. An empty list is false — nothing to satisfy it. */
    record Any(List<Condition> children) implements Condition {
        public Any {
            children = List.copyOf(children);
        }

        @Override
        public boolean test(WorldState state) {
            return children.stream().anyMatch(c -> c.test(state));
        }
    }

    /** Inverts a child condition. */
    record Not(Condition child) implements Condition {
        public Not {
            if (child == null) {
                throw new IllegalArgumentException("Not requires a child condition");
            }
        }

        @Override
        public boolean test(WorldState state) {
            return !child.test(state);
        }
    }

    static Condition always() {
        return new Always();
    }

    static Condition never() {
        return new Never();
    }

    static Condition all(Condition... children) {
        return new All(List.of(children));
    }

    static Condition any(Condition... children) {
        return new Any(List.of(children));
    }

    default Condition and(Condition other) {
        return new All(List.of(this, other));
    }

    default Condition or(Condition other) {
        return new Any(List.of(this, other));
    }

    default Condition negate() {
        return new Not(this);
    }
}
