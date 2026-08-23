package net.cyberpunk042.mcshaders.neoforge.client;

import net.cyberpunk042.mcshaders.vanilla.fog.FogApply;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge's fog application — an event, where Fabric needs a mixin.
 *
 * <p>{@code ViewportEvent.RenderFog} hands over the frame's {@code FogData} through
 * {@code getFogData()}, described by its own javadoc as "the fog parameters that are
 * passed to the shaders. This object is mutable." That is the same object the Fabric
 * mixin reaches at the tail of {@code FogRenderer#setupFog}, so both loaders write
 * through {@link FogApply} and behave identically.
 *
 * <p>Writing the object directly rather than through the event's
 * {@code setNearPlaneDistance} / {@code setFarPlaneDistance} is deliberate: those two
 * assign the <em>environmental</em> pair, and which pair a dimension look should write
 * is the open question recorded in {@link FogApply}. Going through the named setters
 * here would silently make NeoForge answer it differently from Fabric, and then one
 * in-game test would prove nothing about the other loader.
 */
public final class FogHook {

    private FogHook() {
    }

    /** Subscribes to the fog event. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ViewportEvent.RenderFog.class,
                event -> FogApply.apply(event.getFogData()));
    }
}
