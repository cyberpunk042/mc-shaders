package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Holds {@link FieldVisualSchemas} to the measurement it was derived from.
 *
 * <p>A schema is easy to write and hard to check: every control looks plausible. These
 * pin the properties that would actually be wrong if the derivation drifted — a fallback
 * outside its own slider, a bound that no longer admits the content it was widened for,
 * a parameter reappearing that was deliberately left out.
 *
 * <p>The counts are pinned deliberately. They are a property of somebody else's presets
 * and would normally make a brittle assertion, but here the schemas are a <em>generated
 * snapshot</em> of those presets: if the numbers move, the file was regenerated from
 * different evidence and the class documentation describing the split is stale.
 */
class FieldVisualSchemasTest {

    private static final String TYPE = "example:orb";

    private static List<ParamSpec> allSpecs(EffectSchema schema) {
        List<ParamSpec> specs = new ArrayList<>();
        for (String group : schema.groupNames()) {
            specs.addAll(schema.group(group));
        }
        return specs;
    }

    private static double scalar(ParamSpec spec) {
        if (spec.fallback() instanceof ParamValue.Scalar s) {
            return s.value();
        }
        throw new AssertionError(spec.key() + " is not a scalar: " + spec.fallback());
    }

    @Nested
    @DisplayName("the shape of what was generated")
    class Shape {

        @Test
        @DisplayName("both schemas carry the counts the class documentation claims")
        void countsMatchTheDocumentedSplit() {
            EffectSchema orb = FieldVisualSchemas.energyOrb(TYPE);
            EffectSchema geo = FieldVisualSchemas.geodesic(TYPE);

            assertAll(
                    () -> assertEquals(62, allSpecs(orb).size(), "ENERGY_ORB spec count"),
                    () -> assertEquals(19, orb.groupNames().size(), "ENERGY_ORB group count"),
                    () -> assertEquals(13, allSpecs(geo).size(), "GEODESIC spec count"),
                    () -> assertEquals(4, geo.groupNames().size(), "GEODESIC group count"));
        }

        @Test
        @DisplayName("the effect type is the caller's, because these are not our effects")
        void effectTypeIsRequired() {
            assertEquals(TYPE, FieldVisualSchemas.energyOrb(TYPE).effectType());
            assertThrows(IllegalArgumentException.class,
                    () -> FieldVisualSchemas.energyOrb(null));
            assertThrows(IllegalArgumentException.class,
                    () -> FieldVisualSchemas.geodesic("  "));
        }
    }

    @Nested
    @DisplayName("properties a bad derivation would break")
    class Invariants {

        @Test
        @DisplayName("every fallback lies inside its own slider")
        void fallbackIsReachable() {
            for (EffectSchema schema : List.of(
                    FieldVisualSchemas.energyOrb(TYPE), FieldVisualSchemas.geodesic(TYPE))) {
                for (ParamSpec spec : allSpecs(schema)) {
                    double value = scalar(spec);
                    assertTrue(value >= spec.bounds().min() && value <= spec.bounds().max(),
                            () -> spec.key() + " defaults to " + value + ", outside its own "
                                    + "bounds [" + spec.bounds().min() + ", "
                                    + spec.bounds().max() + "] — the control cannot show "
                                    + "the value it starts at");
                }
            }
        }

        @Test
        @DisplayName("a spec's group field matches the group it is filed under")
        void groupFieldAgreesWithFiling() {
            for (EffectSchema schema : List.of(
                    FieldVisualSchemas.energyOrb(TYPE), FieldVisualSchemas.geodesic(TYPE))) {
                for (String group : schema.groupNames()) {
                    for (ParamSpec spec : schema.group(group)) {
                        assertEquals(group, spec.group(),
                                () -> spec.key() + " is filed under '" + group + "' but says "
                                        + "it belongs to '" + spec.group() + "'; an editor "
                                        + "filing by one and looking up by the other loses it");
                    }
                }
            }
        }

        @Test
        @DisplayName("no key appears twice in one schema")
        void keysAreUnique() {
            for (EffectSchema schema : List.of(
                    FieldVisualSchemas.energyOrb(TYPE), FieldVisualSchemas.geodesic(TYPE))) {
                Set<String> seen = new HashSet<>();
                for (ParamSpec spec : allSpecs(schema)) {
                    assertTrue(seen.add(spec.key()),
                            () -> spec.key() + " appears twice; the second control silently "
                                    + "edits the same value as the first");
                }
            }
        }

        @Test
        @DisplayName("the two widened bounds still admit the content they were widened for")
        void widenedBoundsStillAdmitTheirContent() {
            // The whole reason these are not the block's stated 0-1 and 0-2. If a
            // regeneration narrowed them back, an editor would clamp shipped presets.
            ParamSpec coreSize = spec(FieldVisualSchemas.energyOrb(TYPE), "coreSize");
            ParamSpec intensity = spec(FieldVisualSchemas.energyOrb(TYPE), "intensity");

            assertTrue(coreSize.bounds().max() >= 10.0,
                    () -> "coreSize reaches 10 in the presets; bounds stop at "
                            + coreSize.bounds().max());
            assertTrue(intensity.bounds().max() >= 4.32,
                    () -> "intensity reaches 4.32 in the presets; bounds stop at "
                            + intensity.bounds().max());
        }

        @Test
        @DisplayName("the two unexplained keys stayed out")
        void unexplainedKeysAreAbsent() {
            List<String> keys = allSpecs(FieldVisualSchemas.energyOrb(TYPE)).stream()
                    .map(ParamSpec::key).toList();

            assertFalse(keys.contains("animationSpeed"),
                    "animationSpeed has no reader in that mod; a control for it edits nothing");
            assertFalse(keys.contains("previewRadius"),
                    "previewRadius has no reader in that mod; a control for it edits nothing");
        }

        private static ParamSpec spec(EffectSchema schema, String key) {
            return allSpecs(schema).stream().filter(s -> s.key().equals(key)).findFirst()
                    .orElseThrow(() -> new AssertionError("no spec for " + key));
        }
    }

    /**
     * The half that needs the presets, and so cannot run in CI.
     *
     * <p>Same property and same reasoning as {@code FieldContentScanTest}: the content is
     * not in this repository and must not be.
     *
     * <pre>{@code
     * ./gradlew :common:test --tests '*FieldVisualSchemasTest' \
     *     -Dmcshaders.fieldContent=../the-virus-block-mc/config/the-virus-block
     * }</pre>
     */
    @Nested
    @DisplayName("against the presets themselves")
    class AgainstContent {

        private static final String PROPERTY = "mcshaders.fieldContent";

        @Test
        @EnabledIfSystemProperty(named = PROPERTY, matches = ".+")
        @DisplayName("every value any preset sets is reachable from its control")
        void everyPresetValueIsInBounds() {
            Path dir = Path.of(System.getProperty(PROPERTY)).resolve("field_visual");
            assertTrue(Files.isDirectory(dir), () -> dir + " is not a directory");

            Map<String, ParamSpec> orb = index(FieldVisualSchemas.energyOrb(TYPE));
            Map<String, ParamSpec> geo = index(FieldVisualSchemas.geodesic(TYPE));

            int checked = 0;
            List<String> failures = new ArrayList<>();
            for (Path file : listJson(dir)) {
                JsonObject preset = JsonParser.parseString(read(file)).getAsJsonObject();
                Map<String, ParamSpec> specs =
                        "GEODESIC".equals(str(preset, "effectType")) ? geo : orb;
                JsonObject params = preset.getAsJsonObject("params");
                if (params == null) {
                    continue;
                }
                for (String key : params.keySet()) {
                    ParamSpec spec = specs.get(key);
                    if (spec == null || !params.get(key).getAsJsonPrimitive().isNumber()) {
                        continue;
                    }
                    double value = params.get(key).getAsDouble();
                    checked++;
                    if (value < spec.bounds().min() || value > spec.bounds().max()) {
                        failures.add(file.getFileName() + ": " + key + " = " + value
                                + " outside [" + spec.bounds().min() + ", "
                                + spec.bounds().max() + "]");
                    }
                }
            }

            // A pass that checked nothing is the failure this is guarding against.
            assertTrue(checked > 0, () -> "no preset values were checked under " + dir);
            assertTrue(failures.isEmpty(),
                    () -> "an editor built on these schemas would clamp shipped presets:\n  "
                            + String.join("\n  ", failures));
        }

        private static Map<String, ParamSpec> index(EffectSchema schema) {
            Map<String, ParamSpec> byKey = new TreeMap<>();
            for (String group : schema.groupNames()) {
                for (ParamSpec spec : schema.group(group)) {
                    byKey.put(spec.key(), spec);
                }
            }
            return byKey;
        }

        private static String str(JsonObject object, String key) {
            return object.has(key) ? object.get(key).getAsString() : null;
        }

        private static List<Path> listJson(Path dir) {
            try (Stream<Path> listing = Files.list(dir)) {
                return listing.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static String read(Path file) {
            try {
                return Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
