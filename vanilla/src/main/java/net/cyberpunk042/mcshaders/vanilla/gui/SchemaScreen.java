package net.cyberpunk042.mcshaders.vanilla.gui;

import java.util.List;
import java.util.Locale;
import net.cyberpunk042.mcshaders.core.edit.EditSession;
import net.cyberpunk042.mcshaders.core.edit.TuningStore;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.Bounds;
import net.cyberpunk042.mcshaders.core.schema.ControlKind;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import net.cyberpunk042.mcshaders.core.schema.SchemaLayout;
import net.cyberpunk042.mcshaders.core.schema.SliderScale;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * An editing screen built from an effect's schema.
 *
 * <p>Nothing here knows what any particular effect is. An
 * {@link net.cyberpunk042.mcshaders.core.schema.EffectSchema} says what is tunable —
 * groups of {@link ParamSpec}, each with a {@link ControlKind} and a range — and this
 * turns that into widgets and writes what the widgets do back into an
 * {@link EditSession}. Add a parameter to a schema and its control appears; there is
 * nothing to keep in step by hand.
 *
 * <h2>Why it is shaped this way</h2>
 *
 * <p>Two constraints shaped this more than taste did.
 *
 * <p><b>It overrides no render method.</b> 26.2 replaced
 * {@code Screen#render(GuiGraphics, …)} with
 * {@code extractRenderState(GuiGraphicsExtractor, …)}, so a {@code render} override
 * written from memory of an earlier version compiles as a new method nothing calls —
 * the screen comes out blank and no error is raised anywhere. A screen assembled from
 * widgets in {@link #init()} never touches the part of the API that changed. That is
 * how the 26.2 mod this API was read out of does it too.
 *
 * <p><b>It uses only widgets and calls that were read from that mod's source</b>, not
 * remembered. {@code CycleButton} would be the natural control for a toggle and for a
 * choice; it is not used here because the specific builder methods needed
 * ({@code onOffBuilder}, {@code withInitialValue}) appear nowhere in the source that
 * could confirm them, and this cannot be run locally to find out. Both are plain
 * {@link Button}s that change their own label instead — a control that certainly
 * works, rather than a nicer one that might not. See docs/RENDERING-26.2.md.
 *
 * <p>Everything with arithmetic in it lives in {@code core} and is tested:
 * {@link SliderScale} for the 0–1 conversion, {@link EditSession} for coercion and
 * undo. What is left here is assembly — which CI can check by compiling, and a person
 * has to check by looking at it.
 *
 * <h2>What is deliberately unfinished</h2>
 *
 * <p>The list does not scroll. Every row is placed and every widget is added, so a
 * schema taller than the window has controls laid out below it that nothing can
 * reach — and the footer is drawn across whichever row it lands on. {@link
 * SchemaLayout} measures this and {@code FieldVisualLayoutTest} pins the numbers;
 * what to do about it — scroll, paginate, collapse groups — is a design decision and
 * not made here.
 *
 * <p>{@link ControlKind#COLOR} and {@link ControlKind#VECTOR} show their value and are
 * not editable. A colour wants a picker and a vector wants three linked sliders;
 * neither exists in vanilla, and inventing one against an API that cannot be run here
 * is how the blank screen above happens. They are shown rather than skipped so a
 * schema using them displays something instead of a gap.
 */
public final class SchemaScreen extends Screen {

    /**
     * Row heights, margins and how deep the footer sits.
     *
     * <p>In {@code core} rather than here, for the reason this class already gives
     * about {@link SliderScale}: a screen cannot be run in a test, and a list that
     * runs off the bottom of the window is a wrong number rather than a crash. See
     * {@code FieldVisualLayoutTest} — the energy-orb schema is 81 rows and wants a
     * 2000-pixel window, so at ordinary sizes most of it is laid out below the
     * footer.
     */
    private static final SchemaLayout LAYOUT = SchemaLayout.DEFAULT;

    private static final int CONTROL_WIDTH = 200;
    private static final int LABEL_WIDTH = 140;
    private static final int FOOTER_BUTTON_WIDTH = 70;
    private static final int FOOTER_GAP = 4;

    private final EditSession session;
    private final TuningStore store;
    private final Screen parent;

    /**
     * @param parent  the screen to return to on close; null closes to the game
     * @param session the sitting this screen edits — its schema decides the controls
     * @param store   where the results go, so that closing the screen does not discard
     *                them and reopening it does not start again from the defaults
     */
    public SchemaScreen(Screen parent, EditSession session, TuningStore store) {
        super(Component.literal(session.schema().displayName()));
        this.parent = parent;
        this.session = session;
        this.store = store;
    }

    /**
     * Sends the session's values to the store.
     *
     * <p>Called after every change rather than once on close, for two reasons. A
     * renderer reading the store sees the value move while the slider moves, which is
     * the point of tuning a shader from a screen. And closing is not the only way a
     * screen goes away — the game can be quit, the connection can drop — so an editor
     * that only commits on close is an editor that loses work.
     */
    private void publish() {
        store.commit(session);
    }

    @Override
    protected void init() {
        super.init();

        int left = (width - LABEL_WIDTH - CONTROL_WIDTH) / 2;
        int row = 0;

        for (String groupName : session.schema().groupNames()) {
            addLabel(Component.literal(groupName), left, LAYOUT.rowY(row++));

            for (ParamSpec spec : session.schema().group(groupName)) {
                int y = LAYOUT.rowY(row++);
                addLabel(Component.literal(spec.label()), left, y);
                addControl(spec, left + LABEL_WIDTH, y);
            }
        }

        addFooter();
    }

    /**
     * A text label at a position.
     *
     * <p>{@code StringWidget} sizes itself from its text and is placed afterwards; it
     * has no constructor taking a position.
     */
    private void addLabel(Component text, int x, int y) {
        StringWidget label = new StringWidget(text, font);
        label.setPosition(x, y);
        addRenderableWidget(label);
    }

    /** Builds the one widget that edits {@code spec}, by its control kind. */
    private void addControl(ParamSpec spec, int x, int y) {
        switch (spec.control()) {
            case SLIDER, INT_SLIDER ->
                    addRenderableWidget(new ParamSlider(x, y, spec));
            case TOGGLE ->
                    addRenderableWidget(toggleButton(spec, x, y));
            case CHOICE ->
                    addRenderableWidget(choiceButton(spec, x, y));
            // Shown, not editable — see the class documentation.
            case COLOR, VECTOR, LABEL ->
                    addLabel(Component.literal(shownValue(spec)), x, y);
        }
    }

    /** A button that flips a flag and relabels itself. */
    private Button toggleButton(ParamSpec spec, int x, int y) {
        return Button.builder(Component.literal(onOff(flagOf(spec))), button -> {
            boolean next = !flagOf(spec);
            session.set(spec.key(), new ParamValue.Flag(next));
            publish();
            button.setMessage(Component.literal(onOff(next)));
        }).bounds(x, y, CONTROL_WIDTH, LAYOUT.widgetHeight()).build();
    }

    /** A button that advances through a spec's choices and relabels itself. */
    private Button choiceButton(ParamSpec spec, int x, int y) {
        List<String> choices = spec.choices();
        String initial = currentChoice(spec, choices);

        return Button.builder(Component.literal(initial), button -> {
            if (choices.isEmpty()) {
                return;
            }
            int next = (choices.indexOf(currentChoice(spec, choices)) + 1) % choices.size();
            String value = choices.get(next);
            session.set(spec.key(), new ParamValue.Text(value));
            publish();
            button.setMessage(Component.literal(value));
        }).bounds(x, y, CONTROL_WIDTH, LAYOUT.widgetHeight()).build();
    }

    private void addFooter() {
        int footerY = height - LAYOUT.footerHeight();
        int total = FOOTER_BUTTON_WIDTH * 4 + FOOTER_GAP * 3;
        int x = (width - total) / 2;

        x = addFooterButton("Undo", x, footerY, session::undo);
        x = addFooterButton("Redo", x, footerY, session::redo);
        x = addFooterButton("Reset", x, footerY, session::resetAll);
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(x, footerY, FOOTER_BUTTON_WIDTH, LAYOUT.widgetHeight()).build());
    }

    /**
     * A footer button that changes the session and then rebuilds the screen.
     *
     * <p>Undo, redo and reset can each move any number of parameters at once, and every
     * widget holds its own copy of the value it was built with. Rebuilding is what makes
     * the controls show what the session now holds; without it the values change
     * underneath and the screen goes on displaying the old ones.
     *
     * @return the x for the next button
     */
    private int addFooterButton(String text, int x, int y, Runnable change) {
        addRenderableWidget(Button.builder(Component.literal(text), button -> {
            change.run();
            publish();
            rebuildWidgets();
        }).bounds(x, y, FOOTER_BUTTON_WIDTH, LAYOUT.widgetHeight()).build());
        return x + FOOTER_BUTTON_WIDTH + FOOTER_GAP;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private boolean flagOf(ParamSpec spec) {
        return session.get(spec.key()).orElse(null) instanceof ParamValue.Flag flag && flag.value();
    }

    private String currentChoice(ParamSpec spec, List<String> choices) {
        String held = session.get(spec.key()).orElse(null) instanceof ParamValue.Text text
                ? text.value() : null;
        if (held != null && choices.contains(held)) {
            return held;
        }
        return choices.isEmpty() ? "" : choices.get(0);
    }

    private String shownValue(ParamSpec spec) {
        return session.get(spec.key()).map(Object::toString).orElse("-");
    }

    /** A vanilla slider driven by a {@link ParamSpec}'s range. */
    private final class ParamSlider extends AbstractSliderButton {

        private final ParamSpec spec;

        private ParamSlider(int x, int y, ParamSpec spec) {
            super(x, y, CONTROL_WIDTH, LAYOUT.widgetHeight(), Component.empty(),
                    SliderScale.toPosition(spec.bounds(), heldValue(spec)));
            this.spec = spec;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double shown = SliderScale.toValue(spec.bounds(), value);
            setMessage(Component.literal(format(shown) + spec.bounds().unitIfAny().orElse("")));
        }

        @Override
        protected void applyValue() {
            session.set(spec.key(),
                    new ParamValue.Scalar(SliderScale.toValue(spec.bounds(), value)));
            publish();
        }

        private String format(double shown) {
            Bounds bounds = spec.bounds();
            boolean whole = spec.control() == ControlKind.INT_SLIDER
                    || (!bounds.isContinuous() && bounds.step() >= 1);
            return whole ? String.valueOf(Math.round(shown))
                    : String.format(Locale.ROOT, "%.2f", shown);
        }
    }

    private double heldValue(ParamSpec spec) {
        return session.get(spec.key()).orElse(null) instanceof ParamValue.Scalar scalar
                ? scalar.value() : spec.bounds().min();
    }
}
