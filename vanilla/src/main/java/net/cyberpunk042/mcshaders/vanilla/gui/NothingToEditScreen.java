package net.cyberpunk042.mcshaders.vanilla.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What the editor key opens when no effect has declared itself editable.
 *
 * <p>A screen rather than nothing at all. A key that silently does nothing cannot be
 * told apart from a key that is broken, and the first thing anyone does about it is
 * check their controls and then file a bug. Saying why costs one screen.
 *
 * <p>This is the normal state of a fresh install: the mod ships no effects of its own
 * yet, and an effect only becomes editable when something calls
 * {@code McShadersAPI.registerSchema}.
 */
public final class NothingToEditScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    public NothingToEditScreen() {
        super(Component.literal("Nothing to edit"));
    }

    @Override
    protected void init() {
        super.init();

        addLine("No effect has declared itself editable.", height / 2 - 20);
        addLine("An effect becomes editable when a mod registers", height / 2 - 4);
        addLine("a schema for it during initialisation.", height / 2 + 8);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds((width - BUTTON_WIDTH) / 2, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    /**
     * A line of centred text.
     *
     * <p>{@code StringWidget} sizes itself to its content and is positioned afterwards,
     * so centring means measuring first. The measurement takes the {@code String} rather
     * than the {@code Component} because that is the overload confirmed against a mod
     * compiled on this version.
     */
    private void addLine(String text, int y) {
        StringWidget widget = new StringWidget(Component.literal(text), font);
        widget.setPosition((width - font.width(text)) / 2, y);
        addRenderableWidget(widget);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(null);
        }
    }
}
