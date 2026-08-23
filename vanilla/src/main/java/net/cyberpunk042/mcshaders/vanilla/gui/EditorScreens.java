package net.cyberpunk042.mcshaders.vanilla.gui;

import java.util.List;
import net.cyberpunk042.mcshaders.McShadersAPI;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.minecraft.client.gui.screens.Screen;

/**
 * Which screen the editor key should open.
 *
 * <p>Choosing is not loader-specific — only <em>binding a key</em> is — so it lives
 * here and both loaders' key handlers call it. Without this the choice would have been
 * copied, and a mod registering its second schema would start behaving differently on
 * one loader than the other.
 */
public final class EditorScreens {

    private EditorScreens() {
    }

    /**
     * The screen to open right now.
     *
     * <p>Never null. A key that opens nothing is indistinguishable from a key that is
     * broken, so the no-schemas case gets a screen that says so rather than silence.
     */
    public static Screen opening() {
        List<EffectSchema> schemas = McShadersAPI.schemas().all();
        if (schemas.isEmpty()) {
            return new NothingToEditScreen();
        }
        if (schemas.size() == 1) {
            // One editable effect: skip the picker rather than making the player choose
            // from a list of one.
            EffectSchema only = schemas.get(0);
            return new SchemaScreen(
                    null, McShadersAPI.tuning().sessionFor(only), McShadersAPI.tuning());
        }
        return new EffectPickerScreen(schemas);
    }
}
