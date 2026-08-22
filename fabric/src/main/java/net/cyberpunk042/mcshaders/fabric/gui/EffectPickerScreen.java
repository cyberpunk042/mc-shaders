package net.cyberpunk042.mcshaders.fabric.gui;

import java.util.List;
import net.cyberpunk042.mcshaders.core.edit.EditSession;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Which effect to edit, when more than one declared itself editable.
 *
 * <p>A column of buttons rather than a scrolling list. A list widget is the right
 * control for an unbounded number of entries and is also the part of the GUI API with
 * the most surface to get wrong; a column is certainly correct, and the number of
 * effects a pack registers is small. If that stops being true this is the thing to
 * replace, and the schema registry already hands back a list in a stable order to
 * replace it with.
 */
public final class EffectPickerScreen extends Screen {

    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int TOP_MARGIN = 48;

    private final List<EffectSchema> schemas;

    public EffectPickerScreen(List<EffectSchema> schemas) {
        super(Component.literal("Effects"));
        this.schemas = List.copyOf(schemas);
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - BUTTON_WIDTH) / 2;
        StringWidget heading = new StringWidget(
                Component.literal("Choose an effect to edit"), font);
        heading.setPosition(x, TOP_MARGIN - ROW_HEIGHT);
        addRenderableWidget(heading);

        int y = TOP_MARGIN;
        for (EffectSchema schema : schemas) {
            addRenderableWidget(Button.builder(
                    Component.literal(schema.displayName()),
                    button -> open(schema))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
            y += ROW_HEIGHT;
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(x, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /**
     * Opens the editor for one effect, with this screen as its parent.
     *
     * <p>A fresh {@link EditSession} each time, so closing the editor and reopening it
     * starts from the schema's defaults rather than from a half-finished sitting whose
     * undo history reaches back into a previous visit.
     */
    private void open(EffectSchema schema) {
        if (minecraft != null) {
            minecraft.gui.setScreen(new SchemaScreen(this, EditSession.of(schema)));
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(null);
        }
    }
}
