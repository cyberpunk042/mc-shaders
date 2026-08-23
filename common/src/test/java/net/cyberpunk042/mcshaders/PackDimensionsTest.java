package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.cyberpunk042.mcshaders.codec.BindingLoader;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The shipped pack, resolved the way the renderer resolves it.
 *
 * <p>{@code BuiltinBindingsTest} pins one file against the binding the mod registers in
 * Java. This asks a different question: does the pack, read as a pack, produce the look
 * it is supposed to — and does it exercise the format beyond the one condition type the
 * first file uses?
 *
 * <p>It matters because {@code beyond_depths.json} uses only {@code y_range}, and a
 * format with one demonstrated condition type is a format nobody has tried. Every type
 * used below travels through {@code BindingCodec} here rather than in a hand-built
 * fixture, so a field name that does not match the codec fails <em>this</em> test
 * rather than failing silently in someone's world.
 *
 * <p>Deliberately on {@code minecraft:overworld}: it proves a pack can restyle a
 * <em>vanilla</em> dimension, which needs no custom dimension type and is the thing a
 * pack author is most likely to want first.
 */
class PackDimensionsTest {

    private static final String PACK_DIR = "datapack/data/mcshaders/mcshaders/bindings";

    /**
     * The pack directory, found by walking up from wherever the tests were started.
     *
     * <p>Gradle runs these with the module as the working directory and other runners
     * do not, so a relative path is only correct by luck. Same approach as
     * {@code BuiltinBindingsTest} — one convention, not two.
     */
    private static Path packDir() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(PACK_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("could not find " + PACK_DIR + " above " + Path.of("").toAbsolutePath());
    }

    private static final DimensionId OVERWORLD = DimensionId.of("minecraft", "overworld");

    private static BindingRegistry loadPack() {
        Map<String, String> files = new LinkedHashMap<>();
        Path dir = packDir();
        try (var entries = Files.list(dir)) {
            for (Path file : entries.filter(p -> p.toString().endsWith(".json")).toList()) {
                files.put(file.getFileName().toString(), Files.readString(file));
            }
        } catch (IOException e) {
            throw new AssertionError("the shipped pack should be readable from the repo: " + dir, e);
        }
        BindingLoader.Result result = BindingLoader.load(files);
        assertTrue(result.isClean(),
                "the pack this mod ships must load without complaint: " + result.problems());
        return result.registry();
    }

    private static WorldState overworld(Weather weather, boolean submerged) {
        return overworld(weather, submerged, java.util.Set.of());
    }

    private static WorldState overworld(Weather weather, boolean submerged, java.util.Set<String> biomeTags) {
        return new WorldState(OVERWORLD, WorldState.UNKNOWN_DAY_TIME, 64.0, weather, biomeTags, submerged);
    }

    /** As the sampler reports them: the full id, with no leading '#'. */
    private static final java.util.Set<String> FOREST = java.util.Set.of("minecraft:is_forest");

    private static double fogEnd(EffectStack stack) {
        EffectLayer layer = stack.layers().get(0);
        return layer.params().scalar("end").orElseThrow();
    }

    @Nested
    @DisplayName("the pack loads as shipped")
    class Loads {

        @Test
        @DisplayName("every file parses, with no skipped file and no accidental override")
        void packIsClean() {
            BindingRegistry registry = loadPack();

            assertTrue(registry.size() >= 4,
                    "expected beyond_depths plus the three overworld bindings, got " + registry.size());
        }

        @Test
        @DisplayName("the format is exercised beyond the single condition type the first file used")
        void moreThanOneConditionType() {
            // beyond_depths.json is y_range only. A format with one demonstrated
            // condition type is a format nobody has tried.
            BindingRegistry registry = loadPack();

            String conditions = registry.all().stream()
                    .map(b -> b.condition().toString())
                    .reduce("", (a, b) -> a + " " + b);

            assertTrue(conditions.contains("YRange"), "beyond_depths should still be there");
            assertTrue(conditions.contains("InWeather"), "weather should be exercised");
            assertTrue(conditions.contains("Not"), "not should be exercised");
            assertTrue(conditions.contains("Submerged"), "submerged should be exercised");
            assertTrue(conditions.contains("HasBiomeTag"), "biome_tag should be exercised");
        }
    }

    @Nested
    @DisplayName("what the overworld resolves to")
    class Overworld {

        @Test
        @DisplayName("clear weather gets the far, pale atmosphere")
        void clearIsFar() {
            EffectStack stack = loadPack().resolve(overworld(Weather.CLEAR, false));

            assertEquals(1, stack.layers().size(), "the two bindings share a layer id and merge");
            assertEquals(192.0, fogEnd(stack), 0.001);
        }

        @Test
        @DisplayName("a storm closes the fog in, by overriding the same layer id")
        void stormCloses() {
            // The point of the pair: same layer id, higher priority. The storm binding
            // redefines `atmosphere` rather than adding a second layer beside it.
            EffectStack clear = loadPack().resolve(overworld(Weather.CLEAR, false));
            EffectStack storm = loadPack().resolve(overworld(Weather.THUNDER, false));

            assertEquals(1, storm.layers().size());
            assertNotEquals(fogEnd(clear), fogEnd(storm));
            assertTrue(fogEnd(storm) < fogEnd(clear),
                    "a storm should shorten visibility, not lengthen it");
        }

        @Test
        @DisplayName("rain counts as a storm too, which is what the any-of is for")
        void rainAlsoCounts() {
            EffectStack rain = loadPack().resolve(overworld(Weather.RAIN, false));
            EffectStack thunder = loadPack().resolve(overworld(Weather.THUNDER, false));

            assertEquals(fogEnd(thunder), fogEnd(rain), 0.001);
        }

        @Test
        @DisplayName("underwater the storm look is suppressed, because water owns that fog")
        void submergedSuppressesTheStorm() {
            // The not-submerged clause earns its place here: without it a player who
            // dives during a thunderstorm gets storm fog fighting water fog.
            EffectStack submerged = loadPack().resolve(overworld(Weather.THUNDER, true));
            EffectStack clear = loadPack().resolve(overworld(Weather.CLEAR, false));

            assertEquals(fogEnd(clear), fogEnd(submerged), 0.001,
                    "submerged should fall back to the base atmosphere");
        }

        @Test
        @DisplayName("the storm wins on priority, not on sorting after the base by name")
        void stormWinsByPriority() {
            // Found by mutation: dropping the storm to the base's priority changed
            // nothing any other test could see, because BindingRegistry falls back to
            // sorting by id and "overworld_storm" happens to follow "overworld_air".
            // The look was correct by alphabetical accident. Renaming either binding
            // would have broken it silently, so the intent is pinned here.
            BindingRegistry registry = loadPack();

            int base = registry.byId("overworld_air").orElseThrow().priority();
            int storm = registry.byId("overworld_storm").orElseThrow().priority();

            assertTrue(storm > base,
                    "the storm must override the base by priority (" + storm + " vs " + base
                            + "), or it only works while the names sort favourably");
        }

        @Test
        @DisplayName("a forest gets its own haze, matched by a tag written with a leading '#'")
        void forestHaze() {
            // The pack writes "#minecraft:is_forest" the way a pack author would; the
            // sampler reports "minecraft:is_forest" with no '#'. HasBiomeTag strips it,
            // and this is the only place that round trip is exercised end to end.
            EffectStack plain = loadPack().resolve(overworld(Weather.CLEAR, false));
            EffectStack forest = loadPack().resolve(overworld(Weather.CLEAR, false, FOREST));

            assertNotEquals(fogEnd(plain), fogEnd(forest), "the forest look should differ");
            assertEquals(112.0, fogEnd(forest), 0.001);
        }

        @Test
        @DisplayName("a storm in a forest is still a storm — three priorities, one layer")
        void stormBeatsForest() {
            // base 0 < forest 10 < storm 20, all writing `atmosphere`. If priorities
            // were ignored the highest-numbered would not necessarily win.
            EffectStack stormyForest = loadPack().resolve(overworld(Weather.THUNDER, false, FOREST));
            EffectStack stormyPlain = loadPack().resolve(overworld(Weather.THUNDER, false));

            assertEquals(1, stormyForest.layers().size());
            assertEquals(fogEnd(stormyPlain), fogEnd(stormyForest), 0.001,
                    "the storm should override the forest haze, not blend with it");
        }

        @Test
        @DisplayName("a dimension the pack says nothing about is left alone")
        void unboundDimensionUntouched() {
            EffectStack nether = loadPack().resolve(
                    WorldState.of(DimensionId.of("minecraft", "the_nether")));

            assertTrue(nether.layers().isEmpty(),
                    "restyling the overworld must not restyle everything");
        }
    }

    @Nested
    @DisplayName("what it does not claim")
    class Limits {

        @Test
        @DisplayName("no binding depends on time of day, which cannot be read on 26.2")
        void noneDependOnTimeOfDay() {
            // A demo pack gated on an unreadable field would ship a look that never
            // appears, and look exactly like a broken binding.
            BindingRegistry registry = loadPack();

            for (var binding : registry.all()) {
                assertFalse(binding.condition().toString().contains("TimeOfDay"),
                        binding.id() + " is gated on time of day, which never activates on 26.2");
            }
        }
    }
}
