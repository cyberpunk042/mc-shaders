package net.cyberpunk042.mcshaders.core.schema;

import net.cyberpunk042.mcshaders.core.api.Experimental;

/**
 * How tall a schema is when laid out as one row per group heading and per parameter.
 *
 * <p>Here for the same reason as {@link SliderScale}: a screen cannot be run in a
 * test, and this failure mode is a wrong number rather than a crash. A screen that
 * places 81 rows down a 360-pixel window raises nothing, logs nothing and looks
 * exactly like a screen that works — until someone scrolls for a control that is not
 * reachable. Whether a schema fits is arithmetic, so it belongs where arithmetic can
 * be checked.
 *
 * <p>The measurements are a plain vertical list with a fixed footer, which is what
 * the vanilla editor draws. A row is <em>clear of the footer</em> when its whole
 * widget sits above the footer's top edge — a row that merely starts above it has the
 * footer drawn across its control, which is worse than the row being absent.
 *
 * <p>Nothing here scrolls. That is the point: this measures the layout that exists,
 * so the gap between a schema's size and a window's size is a number someone can look
 * at rather than a thing to be noticed in play.
 */
@Experimental(reason = "shaped to measure a fixed list; a scrolling editor would add "
        + "an offset and change what 'visible' means")
public record SchemaLayout(int topMargin, int rowHeight, int widgetHeight, int footerHeight) {

    /** The measurements the vanilla editor is built with. */
    public static final SchemaLayout DEFAULT = new SchemaLayout(32, 24, 20, 28);

    public SchemaLayout {
        require(topMargin >= 0, "topMargin", topMargin);
        require(rowHeight > 0, "rowHeight", rowHeight);
        require(widgetHeight > 0, "widgetHeight", widgetHeight);
        require(footerHeight >= 0, "footerHeight", footerHeight);
    }

    private static void require(boolean ok, String name, int value) {
        if (!ok) {
            throw new IllegalArgumentException(name + " out of range: " + value);
        }
    }

    /**
     * The number of rows {@code schema} occupies: one per group heading, one per
     * parameter.
     *
     * <p>Counted by walking the groups rather than reading
     * {@link EffectSchema#parameterCount()}, because the groups are what a screen
     * iterates. A parameter belonging to no group would be missing from the screen,
     * and counting it here would hide that.
     */
    public static int rowCount(EffectSchema schema) {
        int rows = 0;
        for (String group : schema.groupNames()) {
            rows += 1 + schema.group(group).size();
        }
        return rows;
    }

    /** The y of row {@code index}, counting from zero. */
    public int rowY(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("row index must not be negative: " + index);
        }
        return topMargin + index * rowHeight;
    }

    /** How tall the whole list is, footer excluded. */
    public int heightFor(EffectSchema schema) {
        return topMargin + rowCount(schema) * rowHeight;
    }

    /**
     * How many rows fit above the footer in a window {@code viewportHeight} tall.
     *
     * @return zero when not even the first row clears the footer
     */
    public int rowsClearOfFooter(int viewportHeight) {
        int available = viewportHeight - footerHeight - topMargin - widgetHeight;
        if (available < 0) {
            return 0;
        }
        return available / rowHeight + 1;
    }

    /** Whether every one of {@code schema}'s rows clears the footer. */
    public boolean fitsIn(EffectSchema schema, int viewportHeight) {
        return rowsClearOfFooter(viewportHeight) >= rowCount(schema);
    }

    /**
     * The shortest window in which every row of {@code schema} clears the footer.
     *
     * <p>What to compare a window against, and what a scrolling editor would not need.
     */
    public int viewportNeededFor(EffectSchema schema) {
        int rows = rowCount(schema);
        if (rows == 0) {
            return topMargin + footerHeight;
        }
        return rowY(rows - 1) + widgetHeight + footerHeight;
    }
}
