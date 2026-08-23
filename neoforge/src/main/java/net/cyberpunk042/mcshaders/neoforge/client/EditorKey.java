package net.cyberpunk042.mcshaders.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.vanilla.gui.EditorScreens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The editor key on NeoForge. Which screen it opens is {@link EditorScreens}' business.
 *
 * <p>Registered unbound, as on Fabric: a mod claiming a key the player did not ask for
 * is a nuisance.
 *
 * <h2>Two buses, and they are not the same one</h2>
 *
 * <p>This is the one place in this mod where both NeoForge buses are used at once, and
 * they are not interchangeable:
 *
 * <ul>
 *   <li>{@code RegisterKeyMappingsEvent} is fired on the <strong>mod</strong> bus, per
 *       its own javadoc — which is why {@link #register(IEventBus)} takes one. Every
 *       other subscription in this mod is on the game bus.</li>
 *   <li>{@code ClientTickEvent.Post} is fired on the <strong>game</strong> bus, the
 *       equivalent of Fabric's {@code END_CLIENT_TICK}.</li>
 * </ul>
 *
 * <p>Getting either wrong produces no error and simply never runs.
 *
 * <h2>The category</h2>
 *
 * <p>Registered through the event rather than with {@code KeyMapping.Category.register},
 * which NeoForge <strong>deprecates</strong> in favour of
 * {@code RegisterKeyMappingsEvent#registerCategory} — read from its patch, where the
 * deprecation javadoc names the replacement. The vanilla static also inserts into
 * vanilla's own sort order, so calling both would register the category twice.
 */
public final class EditorKey {

    private static final String TRANSLATION_KEY = "key." + McShaders.MOD_ID + ".open_editor";

    /** Unbound by default. The player chooses a key, or none. */
    private static final int UNBOUND = -1;

    private static KeyMapping mapping;

    private EditorKey() {
    }

    /**
     * Subscribes to both events.
     *
     * @param modEventBus this mod's own bus, which the {@code @Mod} constructor is handed
     */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterKeyMappingsEvent.class, EditorKey::onRegisterKeys);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onTick());
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category =
                new KeyMapping.Category(Identifier.fromNamespaceAndPath(McShaders.MOD_ID, "main"));
        event.registerCategory(category);

        mapping = new KeyMapping(TRANSLATION_KEY, InputConstants.Type.KEYSYM, UNBOUND, category);
        event.register(mapping);
    }

    private static void onTick() {
        if (mapping == null) {
            // The key event has not fired yet, or this is a dedicated server. Neither is
            // a failure.
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        // A loop, not an if: a key pressed several times within one tick queues several
        // clicks, and leaving them queued means the next tick opens the screen again.
        boolean pressed = false;
        while (mapping.consumeClick()) {
            pressed = true;
        }
        if (!pressed || minecraft.gui.screen() != null) {
            return;
        }
        minecraft.gui.setScreen(EditorScreens.opening());
    }
}
