package net.cyberpunk042.mcshaders;

import net.cyberpunk042.mcshaders.core.api.Experimental;
import net.cyberpunk042.mcshaders.fog.FogBackend;
import net.cyberpunk042.mcshaders.fog.FogSink;

/**
 * The backends the mod ships itself, and the one piece of state a mixin can reach.
 *
 * <p>Until this existed the mod contributed no backend at all, so selection always
 * fell through to {@code NoOpBackend} and every resolved effect was compiled and then
 * thrown away. The pipeline ran correctly and drew nothing, which is the hardest kind
 * of nothing to notice.
 *
 * <h2>Why the sink is static</h2>
 *
 * <p>It is the one thing here that is not simply a design preference. A Mixin class is
 * constructed by the Mixin subsystem, not by us, so it cannot be handed a dependency —
 * anything it needs, it must reach statically. Jade's own fog mixin does the same,
 * writing to static fields on its client class.
 *
 * <p>The cost is that there is exactly one sink per JVM, which is correct for a mod
 * and wrong for a test. So {@link FogSink} itself holds no static state: this class
 * has the singleton, and every test constructs its own.
 */
@Experimental
public final class BuiltinBackends {

    private static final FogSink FOG_SINK = new FogSink();

    private BuiltinBackends() {
    }

    /**
     * The fog values resolved for the current frame.
     *
     * <p>Read by the fog mixin, written by {@link FogBackend}. Never null, and before
     * the first frame it reports no fog — so a mixin that runs early leaves vanilla's
     * values alone rather than applying zeroes.
     */
    public static FogSink fog() {
        return FOG_SINK;
    }

    /**
     * Registers everything the mod ships.
     *
     * <p>Called from {@link McShaders#init}, after the effects, because a backend
     * declares which effect types it supports and should not name one that is not
     * registered yet.
     */
    static void register() {
        McShadersAPI.registerBackend(new FogBackend.Factory(FOG_SINK));
    }
}
