package net.cyberpunk042.mcshaders;

import java.util.List;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.param.EffectParams;

/**
 * The looks the mod ships itself.
 *
 * <p>{@code mcshaders:beyond} arrived as two datapack files and nothing that said
 * what it should look like beyond what a {@code dimension_type} can state. That is
 * half of the answer the operator chose — "vanilla base plus mc-shaders dynamic" —
 * and the half that was missing was the dynamic one.
 *
 * <h2>Why there is no base binding</h2>
 *
 * <p>The obvious thing to ship alongside this is a {@code beyond_base} restating the
 * dimension's ordinary fog. It is deliberately absent. The
 * {@code dimension_type} already carries {@code fog_start_distance},
 * {@code fog_end_distance} and the two light colours, so a base binding would be a
 * second copy of numbers that already exist — and two copies of a number drift. The
 * one that a datapack author edits would then not be the one in force, and nothing
 * would say so.
 *
 * <p>So mc-shaders contributes only what the static file cannot express. Above the
 * depth this binding covers it contributes nothing at all, and the vanilla base
 * stands alone — which is the correct outcome, not a gap.
 *
 * <h2>What it does contribute</h2>
 *
 * <p>Fog that closes in as you descend. A {@code dimension_type} states one fog
 * distance for the whole dimension; it has no way to say "and tighter near the
 * floor". A condition-gated binding does, which makes this the smallest honest
 * demonstration that the two halves are doing different jobs.
 *
 * <p>The colour is the dimension's own {@code ambient_light_color}
 * ({@code #0a1418}), darker than its {@code sky_light_color}, so descending reads
 * as the light going out rather than as an unrelated tint appearing.
 *
 * <h2>The same thing as a pack file</h2>
 *
 * <p>{@code datapack/data/mcshaders/mcshaders/bindings/beyond_depths.json} is this
 * binding written in the pack format. The doubled {@code mcshaders} is not a typo:
 * the first is the namespace, the second is this mod's own directory under it, so a
 * third-party pack contributes at {@code data/<their-namespace>/mcshaders/bindings/}
 * without colliding with any other mod that wants a {@code bindings} directory.
 *
 * <p>It is not loaded — wiring the reload listener is still ahead — so it is
 * documentation, and documentation that drifts from the code is
 * worse than none. A test parses that exact file and requires it to equal
 * {@link #beyondDepths()}, so the day the listener is wired the example is already
 * known to be readable.
 */
public final class BuiltinBindings {

    /** The dimension the Ancient City frame will open onto. */
    public static final DimensionId BEYOND = DimensionId.of(McShaders.MOD_ID, "beyond");

    /** Id of the depth-gated binding. */
    public static final String BEYOND_DEPTHS = "beyond_depths";

    /** Id of the layer it contributes, and so the key it merges on. */
    public static final String DEPTH_FOG = "depth_fog";

    /**
     * Below this Y the fog tightens.
     *
     * <p>The dimension is 256 tall with a ceiling and a logical height of 128, so 48
     * is comfortably into the lower half without being the floor itself. It is a
     * starting point chosen from the dimension file, not a tuned value — nothing here
     * has been seen in a running game.
     */
    public static final double DEPTH_BELOW = 48;

    /**
     * The bottom of the dimension.
     *
     * <p>{@code min_y} in {@code beyond.json}. Bounding the condition at the real
     * floor rather than at negative infinity is not only tidier: an unbounded value
     * has no JSON spelling, so the pack-file version of this binding could not say
     * it, and the two would stop being the same thing.
     */
    public static final double DIMENSION_FLOOR = 0;

    private BuiltinBindings() {
    }

    /** Fog that closes in near the floor of the Beyond. */
    public static DimensionBinding beyondDepths() {
        EffectParams params = EffectParams.builder()
                .scalar(BuiltinEffects.START, 1)
                .scalar(BuiltinEffects.END, 24)
                .color(BuiltinEffects.COLOR, 0.039f, 0.078f, 0.094f, 1.0f)
                .build();
        EffectLayer layer = new EffectLayer(
                DEPTH_FOG,
                BuiltinEffects.FOG,
                EffectKind.FOG,
                params,
                BlendMode.ALPHA,
                1.0,
                0);
        return new DimensionBinding(
                BEYOND_DEPTHS,
                BEYOND,
                new Condition.YRange(DIMENSION_FLOOR, DEPTH_BELOW),
                EffectStack.of(List.of(layer)),
                10);
    }

    /**
     * Registers everything the mod ships.
     *
     * <p>Called from {@link McShaders#init}, and in Java rather than left to the pack
     * file, because the pack file is not read yet. Shipping only the JSON would have
     * meant the dimension still looked like nothing while a file in the jar claimed
     * otherwise.
     */
    static void register() {
        McShadersAPI.registerBinding(beyondDepths());
    }
}
