package net.cyberpunk042.mcshaders.fabric.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.McShadersAPI;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * The key that opens {@link SchemaScreen}.
 *
 * <p>A screen nothing can open is a feature that exists only in the source, so this
 * exists at the same time as the screen rather than after it. It is bound to nothing
 * by default: a mod claiming a key the player did not ask for is a nuisance, and an
 * unbound mapping still appears in the controls list to be given one.
 *
 * <p>What it opens comes from {@link McShadersAPI#schemas()} — the effects that
 * declared themselves editable, started from {@link McShadersAPI#tuning()} so that a
 * second press shows what the first visit left rather than the defaults again. One of
 * them opens straight into its editor; several
 * open a list first; none at all shows a screen saying so, rather than a key that
 * appears to do nothing, which is indistinguishable from a key that is broken.
 */
public final class EditorKey {

    private static final String TRANSLATION_KEY = "key." + McShaders.MOD_ID + ".open_editor";

    /**
     * The keycode meaning "not bound to anything".
     *
     * <p>Written as the literal rather than {@code InputConstants.UNKNOWN}, which is a
     * key object where this constructor wants an int. The conversion between them could
     * not be confirmed against a mod that compiles on this version, and -1 is what the
     * unknown key holds — so the literal is the one of the two that is certainly right.
     */
    private static final int UNBOUND = -1;

    private static KeyMapping mapping;

    private EditorKey() {
    }

    /**
     * Registers the mapping and the tick handler that watches it.
     *
     * <p>Called from the client entrypoint. Registering a key mapping has to happen
     * during client initialisation; watching for it has to happen every tick, and
     * {@code consumeClick} is what turns "held down" into one press.
     */
    public static void register() {
        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(McShaders.MOD_ID, "main"));
        mapping = new KeyMapping(TRANSLATION_KEY, InputConstants.Type.KEYSYM, UNBOUND, category);
        KeyMappingHelper.registerKeyMapping(mapping);

        ClientTickEvents.END_CLIENT_TICK.register(EditorKey::onTick);
    }

    private static void onTick(Minecraft minecraft) {
        if (mapping == null) {
            return;
        }
        // A loop, not an if: a key pressed several times within one tick queues several
        // clicks, and leaving them queued means the next tick opens the screen again.
        boolean pressed = false;
        while (mapping.consumeClick()) {
            pressed = true;
        }
        if (!pressed || minecraft.gui.screen() != null) {
            return;
        }
        minecraft.gui.setScreen(openingScreen());
    }

    /**
     * The screen the key opens, given what is registered.
     *
     * <p>Read at press time rather than at registration: schemas are contributed during
     * mod initialisation, which has not finished when the key is registered.
     */
    private static Screen openingScreen() {
        List<EffectSchema> schemas = McShadersAPI.schemas().all();
        if (schemas.isEmpty()) {
            return new NothingToEditScreen();
        }
        if (schemas.size() == 1) {
            EffectSchema only = schemas.get(0);
            return new SchemaScreen(
                    null, McShadersAPI.tuning().sessionFor(only), McShadersAPI.tuning());
        }
        return new EffectPickerScreen(schemas);
    }
}
