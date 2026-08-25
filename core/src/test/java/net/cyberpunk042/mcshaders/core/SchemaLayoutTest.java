package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import net.cyberpunk042.mcshaders.core.schema.SchemaLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The arithmetic that decides whether a schema's controls are reachable. */
class SchemaLayoutTest {

    private static final SchemaLayout L = SchemaLayout.DEFAULT;

    /** A schema of {@code groups} groups holding {@code perGroup} parameters each. */
    private static EffectSchema schema(int groups, int perGroup) {
        EffectSchema.Builder builder = EffectSchema.builder("Test", "test", 1);
        for (int g = 0; g < groups; g++) {
            String group = "G" + g;
            List<ParamSpec> specs = new ArrayList<>();
            for (int p = 0; p < perGroup; p++) {
                specs.add(ParamSpec.slider("g" + g + ".p" + p, "P" + p, 0, 1, 0.5, group));
            }
            builder.group(group, specs);
        }
        return builder.build();
    }

    @Nested
    @DisplayName("row counting")
    class Rows {

        @Test
        @DisplayName("a row per group heading and a row per parameter")
        void headingsCount() {
            assertEquals(0, SchemaLayout.rowCount(schema(0, 0)));
            assertEquals(1, SchemaLayout.rowCount(schema(1, 0)), "a group with no parameters "
                    + "still draws its heading");
            assertEquals(3, SchemaLayout.rowCount(schema(1, 2)));
            assertEquals(9, SchemaLayout.rowCount(schema(3, 2)));
        }

        @Test
        @DisplayName("every parameter is in a group, so none is missing from the count")
        void everyParameterIsReachable() {
            EffectSchema s = schema(4, 3);
            int inGroups = s.groupNames().stream().mapToInt(g -> s.group(g).size()).sum();
            assertEquals(s.parameterCount(), inGroups,
                    "a parameter belonging to no group would never be drawn, because the "
                            + "screen iterates groups. If these ever disagree, rowCount is "
                            + "counting the layout correctly and the schema is hiding "
                            + "parameters");
        }
    }

    @Nested
    @DisplayName("placement")
    class Placement {

        @Test
        @DisplayName("rows start at the top margin and step by the row height")
        void rowsStep() {
            assertEquals(32, L.rowY(0));
            assertEquals(56, L.rowY(1));
            assertEquals(32 + 24 * 80, L.rowY(80));
        }

        @Test
        @DisplayName("height is the top margin plus every row")
        void heightAddsUp() {
            assertEquals(32, L.heightFor(schema(0, 0)));
            assertEquals(32 + 24 * 3, L.heightFor(schema(1, 2)));
        }

        @Test
        void negativeRowsAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> L.rowY(-1));
        }

        @Test
        void nonsensicalMetricsAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> new SchemaLayout(0, 0, 20, 28));
            assertThrows(IllegalArgumentException.class, () -> new SchemaLayout(0, 24, 0, 28));
            assertThrows(IllegalArgumentException.class, () -> new SchemaLayout(-1, 24, 20, 28));
            assertThrows(IllegalArgumentException.class, () -> new SchemaLayout(0, 24, 20, -1));
        }
    }

    @Nested
    @DisplayName("what clears the footer")
    class Footer {

        @Test
        @DisplayName("a row counts only when its whole widget is above the footer")
        void wholeWidgetOrNothing() {
            // Row 0 sits at 32 and is 20 tall, so it needs the footer's top edge at 52
            // or lower: a viewport of 52 + 28 = 80. One pixel less and the footer is
            // drawn across it.
            assertEquals(1, L.rowsClearOfFooter(80));
            assertEquals(0, L.rowsClearOfFooter(79),
                    "a row the footer overlaps is worse than a row that is absent — it "
                            + "looks editable and is not");
        }

        @Test
        @DisplayName("zero, never negative, for a window nothing fits in")
        void tinyWindows() {
            assertEquals(0, L.rowsClearOfFooter(0));
            assertEquals(0, L.rowsClearOfFooter(-100));
        }

        @Test
        @DisplayName("one more row per row height")
        void stepsByRowHeight() {
            assertEquals(1, L.rowsClearOfFooter(80));
            assertEquals(1, L.rowsClearOfFooter(103));
            assertEquals(2, L.rowsClearOfFooter(104));
            assertEquals(12, L.rowsClearOfFooter(360));
        }
    }

    @Nested
    @DisplayName("fitting")
    class Fitting {

        @Test
        @DisplayName("the needed viewport is the smallest one that fits, exactly")
        void neededIsTight() {
            for (int groups = 0; groups <= 4; groups++) {
                for (int perGroup = 0; perGroup <= 4; perGroup++) {
                    EffectSchema s = schema(groups, perGroup);
                    int needed = L.viewportNeededFor(s);
                    assertTrue(L.fitsIn(s, needed),
                            "schema(" + groups + "," + perGroup + ") should fit in " + needed);
                    if (SchemaLayout.rowCount(s) > 0) {
                        assertFalse(L.fitsIn(s, needed - 1),
                                "schema(" + groups + "," + perGroup + ") should not fit in "
                                        + (needed - 1) + "; then " + needed + " is not the "
                                        + "smallest that does");
                    }
                }
            }
        }

        @Test
        @DisplayName("an empty schema needs only its margins")
        void emptyNeedsMargins() {
            assertEquals(32 + 28, L.viewportNeededFor(schema(0, 0)));
        }
    }
}
