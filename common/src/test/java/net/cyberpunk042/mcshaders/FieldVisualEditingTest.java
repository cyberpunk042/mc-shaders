package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.cyberpunk042.mcshaders.core.edit.EditSession;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import net.cyberpunk042.mcshaders.core.edit.TuningStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * {@link FieldVisualSchemas} driven through the editing stack that would host it.
 *
 * <p>The schemas are the first realistic ones this repository has: {@code mcshaders:fog}
 * describes three parameters and {@code ENERGY_ORB} describes 62. Everything in
 * {@code EditSession} and {@code TuningStore} was written and tested against the small
 * one, so this exists to find what only shows up at the larger size.
 *
 * <p>The load-bearing test is {@link Coercion}. {@code ParamSpec.coerce} clamps a bounded
 * scalar to its bounds, and {@code EditSession.set} runs every value through it — so a
 * bound that is too narrow does not raise anything, it silently rewrites the value on its
 * way in. That is the exact failure the bounds were widened to avoid, and asserting the
 * arithmetic in isolation would not have caught it: this drives the real path.
 */
class FieldVisualEditingTest {

    private static final String TYPE = "example:orb";
    private static final String GEO = "example:geodesic";

    private static List<ParamSpec> specs(EffectSchema schema) {
        List<ParamSpec> all = new ArrayList<>();
        for (String group : schema.groupNames()) {
            all.addAll(schema.group(group));
        }
        return all;
    }

    private static double valueOf(EditSession session, String key) {
        return ((ParamValue.Scalar) session.get(key).orElseThrow()).value();
    }

    @Nested
    @DisplayName("values survive the trip in")
    class Coercion {

        @Test
        @DisplayName("the values that motivated widening are not clamped on the way in")
        void widenedValuesSurviveSet() {
            EditSession orb = EditSession.of(FieldVisualSchemas.energyOrb(TYPE));
            EditSession geo = EditSession.of(FieldVisualSchemas.geodesic(GEO));

            // Each of these is a value the block's own documentation would have excluded.
            orb.set("coreSize", new ParamValue.Scalar(10.0));
            orb.set("intensity", new ParamValue.Scalar(4.32));
            orb.set("v2CoronaBrightness", new ParamValue.Scalar(1.0));
            geo.set("geoDomeClip", new ParamValue.Scalar(1.0));

            assertEquals(10.0, valueOf(orb, "coreSize"), 1e-9,
                    "documented 0-1; presets reach 10. A narrower bound would clamp here "
                            + "with no error, and the preset would quietly render wrong.");
            assertEquals(4.32, valueOf(orb, "intensity"), 1e-9, "documented 0-2");
            assertEquals(1.0, valueOf(orb, "v2CoronaBrightness"), 1e-9,
                    "comment default 0.15; three presets set 1.0");
            assertEquals(1.0, valueOf(geo, "geoDomeClip"), 1e-9,
                    "the comment enumerates 1=flat although no preset uses it");
        }

        @Test
        @DisplayName("a value genuinely outside its bounds is still clamped")
        void outOfBoundsIsStillClamped() {
            EditSession orb = EditSession.of(FieldVisualSchemas.energyOrb(TYPE));
            ParamSpec coreSize = specs(FieldVisualSchemas.energyOrb(TYPE)).stream()
                    .filter(s -> s.key().equals("coreSize")).findFirst().orElseThrow();

            orb.set("coreSize", new ParamValue.Scalar(coreSize.bounds().max() + 1000));

            assertEquals(coreSize.bounds().max(), valueOf(orb, "coreSize"), 1e-9,
                    "widening the bounds must not have disabled clamping altogether — "
                            + "otherwise the test above passes for the wrong reason");
        }
    }

    @Nested
    @DisplayName("the stack at this size")
    class AtScale {

        @Test
        @DisplayName("an untouched store hands back every parameter at its schema default")
        void effectiveStartsFromTheSchema() {
            EffectSchema schema = FieldVisualSchemas.energyOrb(TYPE);
            EffectParams effective = new TuningStore().effective(schema);

            for (ParamSpec spec : specs(schema)) {
                assertTrue(effective.get(spec.key()).isPresent(),
                        () -> spec.key() + " is described by the schema but absent from "
                                + "effective(), so an editor would show a control with "
                                + "nothing behind it");
                assertEquals(spec.fallback(), effective.get(spec.key()).orElseThrow(),
                        () -> spec.key() + " does not start at the value its spec declares");
            }
        }

        @Test
        @DisplayName("one pass over every parameter fits in the default history")
        void aFullPassFitsInHistory() {
            EffectSchema schema = FieldVisualSchemas.energyOrb(TYPE);
            EditSession session = EditSession.of(schema);
            EffectParams start = session.current();

            int applied = 0;
            for (ParamSpec spec : specs(schema)) {
                double from = ((ParamValue.Scalar) spec.fallback()).value();
                double to = Math.min(spec.bounds().max(), from + 1);
                if (session.set(spec.key(), new ParamValue.Scalar(to))) {
                    applied++;
                }
            }

            // Measured, not assumed: 62 parameters yield 53 effective edits, because
            // eleven specs already sit at the top of their own bounds and nudging them
            // up changes nothing. That is 53 of the 64 default slots for a single pass,
            // so a second pass would start dropping the oldest states.
            assertTrue(applied > 0, "no edit took effect at all");
            assertTrue(session.historyDepth() <= EditSession.DEFAULT_HISTORY_LIMIT,
                    () -> "one pass already overflowed history: " + session.historyDepth()
                            + " of " + EditSession.DEFAULT_HISTORY_LIMIT);

            while (session.undo()) {
                // wind all the way back
            }
            assertEquals(start, session.current(),
                    "undo could not reach the values this sitting started from");
        }

        @Test
        @DisplayName("resetAll returns to the start even when undo could not")
        void resetAllIsNotBoundedByHistory() {
            EffectSchema schema = FieldVisualSchemas.energyOrb(TYPE);
            EditSession session = EditSession.of(schema);
            EffectParams start = session.current();

            for (int i = 0; i < EditSession.DEFAULT_HISTORY_LIMIT * 2; i++) {
                session.set("intensity", new ParamValue.Scalar(0.1 + i * 0.01));
            }
            assertTrue(session.isDirty());

            session.resetAll();
            assertEquals(start, session.current(),
                    "resetAll applies the original directly and must not depend on how "
                            + "much history survived");
        }

        @Test
        @DisplayName("a tuned value round-trips through the store, and its neighbours do not move")
        void tuningRoundTrips() {
            EffectSchema schema = FieldVisualSchemas.energyOrb(TYPE);
            TuningStore store = new TuningStore();

            EditSession session = store.sessionFor(schema);
            session.set("coreSize", new ParamValue.Scalar(7.5));
            store.commit(session);

            EffectParams effective = store.effective(schema);
            assertEquals(new ParamValue.Scalar(7.5), effective.get("coreSize").orElseThrow(),
                    "the committed value should be what the store hands back");
            assertTrue(store.isTuned(TYPE));

            for (ParamSpec spec : specs(schema)) {
                if (spec.key().equals("coreSize")) {
                    continue;
                }
                assertEquals(spec.fallback(), effective.get(spec.key()).orElseThrow(),
                        () -> "tuning coreSize moved " + spec.key() + " as well");
            }
        }
    }

    /**
     * The half that needs the presets. Same property and reasoning as
     * {@code FieldContentScanTest}: that content is not in this repository.
     */
    @Nested
    @DisplayName("against the presets themselves")
    class AgainstContent {

        private static final String PROPERTY = "mcshaders.fieldContent";

        @Test
        @EnabledIfSystemProperty(named = PROPERTY, matches = ".+")
        @DisplayName("no value any preset sets is rewritten on its way through an editor")
        void noPresetValueIsCoerced() {
            Path dir = Path.of(System.getProperty(PROPERTY)).resolve("field_visual");
            assertTrue(Files.isDirectory(dir), () -> dir + " is not a directory");

            EditSession orb = EditSession.of(FieldVisualSchemas.energyOrb(TYPE));
            EditSession geo = EditSession.of(FieldVisualSchemas.geodesic(GEO));

            int checked = 0;
            List<String> rewritten = new ArrayList<>();
            for (Path file : listJson(dir)) {
                JsonObject preset = JsonParser.parseString(read(file)).getAsJsonObject();
                boolean isGeodesic = preset.has("effectType")
                        && "GEODESIC".equals(preset.get("effectType").getAsString());
                EditSession session = isGeodesic ? geo : orb;
                JsonObject params = preset.getAsJsonObject("params");
                if (params == null) {
                    continue;
                }
                for (String key : params.keySet()) {
                    if (session.schema().parameter(key).isEmpty()
                            || !params.get(key).getAsJsonPrimitive().isNumber()) {
                        continue;
                    }
                    double authored = params.get(key).getAsDouble();
                    session.set(key, new ParamValue.Scalar(authored));
                    checked++;
                    double after = valueOf(session, key);
                    if (Math.abs(after - authored) > 1e-9) {
                        rewritten.add(file.getFileName() + ": " + key + " " + authored
                                + " became " + after);
                    }
                }
            }

            assertTrue(checked > 0, () -> "no preset values were driven through " + dir);
            assertTrue(rewritten.isEmpty(),
                    () -> "an editor would silently rewrite these authored values:\n  "
                            + String.join("\n  ", rewritten));
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
