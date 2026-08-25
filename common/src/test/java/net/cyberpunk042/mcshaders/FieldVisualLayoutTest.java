package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import net.cyberpunk042.mcshaders.core.schema.SchemaLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * How much of each field-visual schema the vanilla editor can actually reach.
 *
 * <p>{@code FieldVisualEditingTest} shows the schemas hold every value the shipped
 * presets use — nothing clamps. This is the other half of the same question: whether
 * a person can get at those values through the screen that exists to edit them. The
 * screen lays parameters out as a plain vertical list with a fixed footer and does not
 * scroll, so the answer is arithmetic, and {@link SchemaLayout} is where it lives.
 *
 * <p>The answer for {@code ENERGY_ORB} is no, at any window size. That is a finding,
 * not a failure of these tests: they pin the numbers so the gap is a thing someone can
 * look at, and so that whatever closes it — scrolling, pages, collapsing groups — has
 * something to be measured against.
 */
class FieldVisualLayoutTest {

    private static final SchemaLayout L = SchemaLayout.DEFAULT;
    private static final EffectSchema ORB = FieldVisualSchemas.energyOrb("energy_orb");
    private static final EffectSchema GEODESIC = FieldVisualSchemas.geodesic("geodesic");

    /** How many of the first {@code rows} rows are parameters rather than headings. */
    private static int paramsWithin(EffectSchema schema, int rows) {
        int seen = 0;
        int params = 0;
        for (String group : schema.groupNames()) {
            if (seen++ >= rows) {
                return params;
            }
            for (ParamSpec ignored : schema.group(group)) {
                if (seen++ >= rows) {
                    return params;
                }
                params++;
            }
        }
        return params;
    }

    @Nested
    @DisplayName("ENERGY_ORB")
    class Orb {

        @Test
        @DisplayName("81 rows, needing a 2000-pixel window")
        void size() {
            assertEquals(19, ORB.groupNames().size());
            assertEquals(62, ORB.parameterCount());
            assertEquals(81, SchemaLayout.rowCount(ORB), "19 headings plus 62 parameters");
            assertEquals(2000, L.viewportNeededFor(ORB));
        }

        @Test
        @DisplayName("a 1080-tall window at scale 1 reaches half of it")
        void theGenerousCase() {
            // GUI pixels, not screen pixels: Minecraft divides the window by the GUI
            // scale, so 1080 here already assumes a 1080-pixel-tall window at scale 1.
            // Fitting all 81 rows needs 2000, which is a 4K display with the scale
            // forced to 1 — possible, at a size where the text cannot be read.
            assertFalse(L.fitsIn(ORB, 1080));
            assertTrue(L.fitsIn(ORB, 2000), "and 2000 is exactly what it takes");
            assertEquals(42, Math.min(L.rowsClearOfFooter(1080), SchemaLayout.rowCount(ORB)));
            assertEquals(31, paramsWithin(ORB, L.rowsClearOfFooter(1080)),
                    "half the parameters, in the best case anyone has");
        }

        @Test
        @DisplayName("in the ordinary case, eight of sixty-two")
        void theOrdinaryCase() {
            // 1920x1080 at GUI scale 3 is 640x360, which is what most of this gets
            // looked at on.
            assertEquals(12, L.rowsClearOfFooter(360));
            assertEquals(8, paramsWithin(ORB, 12),
                    "the other 54 are laid out below the window and below the footer, "
                            + "with no scroll to reach them");
        }
    }

    @Nested
    @DisplayName("GEODESIC")
    class Geodesic {

        @Test
        @DisplayName("17 rows, needing 464 pixels — small enough to fit, but not everywhere")
        void size() {
            assertEquals(17, SchemaLayout.rowCount(GEODESIC));
            assertEquals(464, L.viewportNeededFor(GEODESIC));
            assertTrue(L.fitsIn(GEODESIC, 480));
            assertFalse(L.fitsIn(GEODESIC, 360),
                    "so even the small schema is cut off at the common window size");
        }

        @Test
        @DisplayName("nine of thirteen at 360")
        void theOrdinaryCase() {
            assertEquals(9, paramsWithin(GEODESIC, L.rowsClearOfFooter(360)));
        }
    }
}
