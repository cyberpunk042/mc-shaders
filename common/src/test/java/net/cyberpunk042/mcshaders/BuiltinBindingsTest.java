package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cyberpunk042.mcshaders.codec.BindingCodec;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Whether the Beyond looks like anything.
 *
 * <p>It shipped as two datapack files and no binding, so a dimension existed that
 * mc-shaders had nothing to say about — the same shape of gap as an effect registry
 * with nothing in it. These tests are mostly about the two ways that gap could
 * quietly reopen.
 *
 * <p>The first is the pack file. It is documentation of the format, it is not loaded
 * by anything yet, and an example nothing executes rots. So it is parsed here and
 * required to be the binding the mod actually registers.
 *
 * <p>The second is the condition. A depth-gated look that turns out to be always-on,
 * or never-on, would leave no trace in a screenshot anybody would question.
 */
class BuiltinBindingsTest {

    private static final String PACK_PATH =
            "datapack/data/mcshaders/mcshaders/bindings/beyond_depths.json";

    /**
     * Finds the shipped pack file by walking up from wherever the test is running.
     *
     * <p>A fixed relative path would depend on the test task's working directory,
     * which nothing here sets and which differs between Gradle and a bare JUnit run.
     * That would fail as though the file were wrong when it is merely elsewhere, so
     * the search is explicit and reports what it looked at.
     */
    private static Path packFile() {
        Path at = Path.of("").toAbsolutePath();
        for (Path dir = at; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(PACK_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("No " + PACK_PATH + " in " + at + " or any parent");
    }

    private static WorldState atDepth(double y) {
        return WorldState.of(BuiltinBindings.BEYOND).withYLevel(y);
    }

    @Nested
    @DisplayName("the pack file and the code")
    class PackFile {

        @Test
        @DisplayName("the shipped example parses to exactly what the mod registers")
        void exampleMatchesCode() throws IOException {
            Path file = packFile();
            String json = Files.readString(file);

            DimensionBinding fromFile = BindingCodec.read(json, file.toString());

            assertEquals(BuiltinBindings.beyondDepths(), fromFile);
        }

        @Test
        @DisplayName("survives a trip out through the writer and back")
        void roundTrips() {
            DimensionBinding original = BuiltinBindings.beyondDepths();

            String written = BindingCodec.write(original).toString();
            DimensionBinding reread = BindingCodec.read(written, "round-trip");

            assertEquals(original, reread);
        }
    }

    @Nested
    @DisplayName("the depth gate")
    class DepthGate {

        @Test
        @DisplayName("applies near the floor")
        void appliesLow() {
            assertTrue(BuiltinBindings.beyondDepths().appliesTo(atDepth(0)));
            assertTrue(BuiltinBindings.beyondDepths().appliesTo(atDepth(47)));
        }

        @Test
        @DisplayName("does not apply above the depth it covers")
        void doesNotApplyHigh() {
            assertFalse(
                    BuiltinBindings.beyondDepths().appliesTo(atDepth(49)),
                    "above this the vanilla dimension_type stands alone, which is the design");
            assertFalse(BuiltinBindings.beyondDepths().appliesTo(atDepth(200)));
        }

        @Test
        @DisplayName("does not apply in another dimension")
        void doesNotLeakToOtherDimensions() {
            WorldState nether =
                    WorldState.of(DimensionId.minecraft("the_nether")).withYLevel(10);

            assertFalse(BuiltinBindings.beyondDepths().appliesTo(nether));
        }
    }

    @Nested
    @DisplayName("what it contributes")
    class Contribution {

        @Test
        @DisplayName("one fog layer, of the effect type the mod actually registers")
        void namesARegisteredEffect() {
            DimensionBinding binding = BuiltinBindings.beyondDepths();

            assertEquals(1, binding.stack().layers().size());
            EffectLayer layer = binding.stack().layers().get(0);
            assertEquals(BuiltinEffects.FOG, layer.type(),
                    "a binding naming an effect type nothing registers renders nothing");
        }

        @Test
        @DisplayName("sets every parameter the fog effect has")
        void setsEveryFogParameter() {
            EffectLayer layer = BuiltinBindings.beyondDepths().stack().layers().get(0);

            for (String key : BuiltinEffects.fogDefaults().asMap().keySet()) {
                assertTrue(
                        layer.params().asMap().containsKey(key),
                        "leaves '" + key + "' to a default the effect may change");
            }
        }

        @Test
        @DisplayName("closes in tighter than the dimension_type's own fog")
        void tighterThanTheStaticBase() {
            EffectLayer layer = BuiltinBindings.beyondDepths().stack().layers().get(0);
            double end = layer.params().scalarOr(BuiltinEffects.END, -1);

            // beyond.json states fog_end_distance 64. Contributing something looser
            // than the file it is meant to deepen would be visible as nothing.
            assertTrue(end < 64, "fog end " + end + " is no tighter than the static base's 64");
        }

        @Test
        @DisplayName("outranks an unprioritised binding on the same dimension")
        void outranksTheDefaultPriority() {
            assertTrue(
                    BuiltinBindings.beyondDepths().priority() > 0,
                    "a conditional addition that loses to a base binding never shows");
        }
    }
}
