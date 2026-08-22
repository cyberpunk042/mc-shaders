package net.cyberpunk042.mcshaders.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Whether a pack can be written as JSON and come back unchanged.
 *
 * <p>Round-tripping is the load-bearing test here, not a decorative one. A codec
 * has two halves that can drift apart silently: a writer that forgets a field and a
 * reader that never looked for it agree perfectly with each other and lose data.
 * Comparing the value that went in with the value that came out is what catches
 * that, and it catches it for every field at once.
 *
 * <p>The second half of these tests is about error messages, because "the pack is
 * wrong" is worthless and "nether.json at stack.layers[0].params.speed: expected a
 * number, found a string" is actionable.
 */
class BindingCodecTest {

    private static final Gson GSON = new Gson();

    private static String roundTripText(DimensionBinding binding) {
        return GSON.toJson(BindingCodec.write(binding));
    }

    private static DimensionBinding roundTrip(DimensionBinding binding) {
        return BindingCodec.read(roundTripText(binding), "round-trip");
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a full binding survives being written and read back")
        void fullBindingSurvives() {
            EffectLayer layer = new EffectLayer(
                    "heat_haze",
                    "example:shimmer",
                    EffectKind.DISTORT,
                    EffectParams.builder()
                            .scalar("speed", 0.5)
                            .flag("on", true)
                            .text("label", "wobble")
                            .vec3("dir", 1, 0, -1)
                            .color("tint", 1.0f, 0.6f, 0.4f, 0.8f)
                            .build(),
                    BlendMode.ADD,
                    0.75,
                    3);

            DimensionBinding binding = new DimensionBinding(
                    "nether_base",
                    DimensionId.minecraft("the_nether"),
                    new Condition.TimeOfDay(13000, 23000),
                    EffectStack.of(layer),
                    5);

            assertEquals(binding, roundTrip(binding));
        }

        @Test
        @DisplayName("every parameter shape survives, including the ones that look alike")
        void everyParamShapeSurvives() {
            DimensionBinding binding = bindingWith(EffectParams.builder()
                    .scalar("whole", 3)          // a double that prints without a fraction
                    .scalar("fraction", 0.125)
                    .scalar("negative", -7.5)
                    .flag("yes", true)
                    .flag("no", false)
                    .text("name", "a string that is not a number")
                    .text("numeric_text", "42")  // must not come back as a Scalar
                    .vec3("zero", 0, 0, 0)
                    .color("black", 0f, 0f, 0f, 0f)
                    .build());

            assertEquals(binding, roundTrip(binding));
        }

        @Test
        @DisplayName("all ten condition types survive")
        void everyConditionSurvives() {
            List<Condition> conditions = List.of(
                    Condition.always(),
                    Condition.never(),
                    new Condition.Submerged(),
                    new Condition.TimeOfDay(0, 100),
                    new Condition.YRange(-64, 320),
                    new Condition.InWeather(Weather.THUNDER),
                    new Condition.HasBiomeTag("c:is_hot"),
                    Condition.all(Condition.always(), new Condition.Submerged()),
                    Condition.any(Condition.never(), new Condition.YRange(0, 1)),
                    new Condition.Not(new Condition.InWeather(Weather.CLEAR)));

            for (Condition condition : conditions) {
                DimensionBinding binding = new DimensionBinding(
                        "b", DimensionId.minecraft("overworld"),
                        condition, EffectStack.empty(), 0);
                assertEquals(binding, roundTrip(binding),
                        () -> "condition did not survive: " + condition);
            }
        }

        @Test
        @DisplayName("a nested condition tree survives with its structure intact")
        void nestedConditionsSurvive() {
            Condition condition = Condition.all(
                    new Condition.TimeOfDay(13000, 23000),
                    Condition.any(
                            new Condition.InWeather(Weather.RAIN),
                            new Condition.Not(new Condition.HasBiomeTag("c:is_dry"))),
                    new Condition.YRange(0, 64));

            DimensionBinding binding = new DimensionBinding(
                    "b", DimensionId.minecraft("overworld"), condition, EffectStack.empty(), 0);

            assertEquals(binding, roundTrip(binding));
        }

        @Test
        @DisplayName("layer order is preserved, because render order depends on it")
        void layerOrderPreserved() {
            EffectStack stack = EffectStack.of(
                    EffectLayer.of("first", EffectKind.FOG, EffectParams.empty()),
                    EffectLayer.of("second", EffectKind.BLOOM, EffectParams.empty()),
                    EffectLayer.of("third", EffectKind.GRAIN, EffectParams.empty()));

            DimensionBinding read = roundTrip(new DimensionBinding(
                    "b", DimensionId.minecraft("overworld"), Condition.always(), stack, 0));

            assertEquals(List.of("first", "second", "third"),
                    read.stack().layers().stream().map(EffectLayer::id).toList());
        }

        @Test
        @DisplayName("a layer with no definition type keeps having none")
        void nullTypeStaysNull() {
            DimensionBinding binding = new DimensionBinding(
                    "b", DimensionId.minecraft("overworld"), Condition.always(),
                    EffectStack.of(EffectLayer.of("l", EffectKind.FOG, EffectParams.empty())), 0);

            assertEquals(binding, roundTrip(binding));
        }

        @Test
        @DisplayName("several bindings survive as an array")
        void manySurvive() {
            List<DimensionBinding> bindings = List.of(
                    DimensionBinding.of("a", DimensionId.minecraft("overworld"), EffectStack.empty()),
                    DimensionBinding.of("b", DimensionId.parse("example:void"), EffectStack.empty()));

            String json = GSON.toJson(BindingCodec.writeAll(bindings));
            assertEquals(bindings,
                    BindingCodec.readAll(new java.io.StringReader(json), "many"));
        }
    }

    @Nested
    @DisplayName("what a pack may leave out")
    class Defaults {

        @Test
        @DisplayName("only id and dimension are required")
        void minimalBinding() {
            DimensionBinding binding = BindingCodec.read(
                    "{\"id\":\"b\",\"dimension\":\"minecraft:overworld\"}", "minimal.json");

            assertEquals("b", binding.id());
            assertEquals(DimensionId.minecraft("overworld"), binding.dimension());
            assertEquals(Condition.always(), binding.condition());
            assertTrue(binding.stack().layers().isEmpty());
            assertEquals(0, binding.priority());
        }

        @Test
        @DisplayName("a layer defaults to alpha blend, full weight and zero priority")
        void minimalLayer() {
            DimensionBinding binding = BindingCodec.read("""
                    {"id":"b","dimension":"minecraft:overworld",
                     "stack":{"layers":[{"id":"l","kind":"fog"}]}}""", "minimal.json");

            EffectLayer layer = binding.stack().layers().get(0);
            assertEquals(BlendMode.ALPHA, layer.blend());
            assertEquals(1.0, layer.weight(), 1e-9);
            assertEquals(0, layer.priority());
            assertTrue(layer.params().isEmpty());
        }

        @Test
        @DisplayName("enum names are read whatever case they are written in")
        void enumsAreCaseInsensitive() {
            DimensionBinding binding = BindingCodec.read("""
                    {"id":"b","dimension":"minecraft:overworld",
                     "stack":{"layers":[{"id":"l","kind":"COLOR_GRADE","blend":"Screen"}]}}""",
                    "case.json");

            EffectLayer layer = binding.stack().layers().get(0);
            assertEquals(EffectKind.COLOR_GRADE, layer.kind());
            assertEquals(BlendMode.SCREEN, layer.blend());
        }

        @Test
        @DisplayName("a colour may omit alpha, which means opaque")
        void colourAlphaDefaults() {
            DimensionBinding binding = BindingCodec.read("""
                    {"id":"b","dimension":"minecraft:overworld","stack":{"layers":[
                      {"id":"l","kind":"fog","params":{"tint":{"r":1,"g":0,"b":0}}}]}}""",
                    "colour.json");

            assertEquals(new ParamValue.Rgba(1f, 0f, 0f, 1f),
                    binding.stack().layers().get(0).params().get("tint").orElseThrow());
        }
    }

    @Nested
    @DisplayName("errors a pack author can act on")
    class Errors {

        private static PackException failing(String json) {
            return assertThrows(PackException.class,
                    () -> BindingCodec.read(json, "nether.json"));
        }

        @Test
        @DisplayName("the failure names the file and the path to the field")
        void namesFileAndPath() {
            PackException e = failing("""
                    {"id":"b","dimension":"minecraft:overworld","stack":{"layers":[
                      {"id":"l","kind":"fog","params":{"dir":[1,"two",3]}}]}}""");

            assertEquals("nether.json", e.source());
            assertEquals("stack.layers[0].params.dir[1]", e.path());
            assertTrue(e.getMessage().contains("nether.json at stack.layers[0].params.dir[1]"),
                    () -> "message was: " + e.getMessage());
        }

        @Test
        @DisplayName("the index in the message is the failing layer, not the first")
        void namesTheFailingIndex() {
            PackException e = failing("""
                    {"id":"b","dimension":"minecraft:overworld","stack":{"layers":[
                      {"id":"ok","kind":"fog"},
                      {"id":"ok2","kind":"bloom"},
                      {"id":"bad","kind":"not_a_kind"}]}}""");

            assertEquals("stack.layers[2].kind", e.path());
        }

        @Test
        @DisplayName("an unknown enum lists the ones that would have worked")
        void unknownEnumListsAlternatives() {
            PackException e = failing("""
                    {"id":"b","dimension":"minecraft:overworld",
                     "stack":{"layers":[{"id":"l","kind":"sparkles"}]}}""");

            assertTrue(e.problem().contains("'sparkles' is not one of"), e::getMessage);
            assertTrue(e.problem().contains("color_grade"), e::getMessage);
            assertTrue(e.problem().contains("fog"), e::getMessage);
        }

        @Test
        @DisplayName("an unknown condition type lists the ones that would have worked")
        void unknownConditionListsAlternatives() {
            PackException e = failing("""
                    {"id":"b","dimension":"minecraft:overworld",
                     "condition":{"type":"on_tuesdays"}}""");

            assertEquals("condition.type", e.path());
            assertTrue(e.problem().contains("time_of_day"), e::getMessage);
        }

        @Test
        @DisplayName("a missing required field says which one")
        void missingFieldNamesIt() {
            assertTrue(failing("{\"dimension\":\"minecraft:overworld\"}")
                    .problem().contains("missing 'id'"));
            assertTrue(failing("{\"id\":\"b\"}")
                    .problem().contains("missing 'dimension'"));
        }

        @Test
        @DisplayName("a vector of the wrong length says so, with its length")
        void wrongVectorLength() {
            PackException e = failing("""
                    {"id":"b","dimension":"minecraft:overworld","stack":{"layers":[
                      {"id":"l","kind":"fog","params":{"dir":[1,2]}}]}}""");

            assertEquals("stack.layers[0].params.dir", e.path());
            assertTrue(e.problem().contains("exactly three"), e::getMessage);
            assertTrue(e.problem().contains("found 2"), e::getMessage);
        }

        @Test
        @DisplayName("a model invariant is reported at the pack's coordinates, not as a raw throw")
        void modelInvariantBecomesPackError() {
            PackException e = failing("""
                    {"id":"b","dimension":"minecraft:overworld",
                     "stack":{"layers":[{"id":"","kind":"fog"}]}}""");

            assertEquals("stack.layers[0]", e.path());
            assertTrue(e.problem().toLowerCase(java.util.Locale.ROOT).contains("blank"),
                    e::getMessage);
        }

        @Test
        @DisplayName("a quoted number is text, not an error — the cost of shape typing")
        void quotedNumberIsTextNotAnError() {
            // Deliberately pinned rather than fixed. Params are typed by JSON shape,
            // so a string is a Text param and the codec has no way to know the author
            // meant a number: Text params are legitimate, and nothing here knows what
            // "speed" is supposed to be. EffectSchema is the layer that does know, and
            // SchemaAudit is where this gets caught. If that ever changes, this test
            // should fail rather than the behaviour drift silently.
            DimensionBinding binding = BindingCodec.read("""
                    {"id":"b","dimension":"minecraft:overworld","stack":{"layers":[
                      {"id":"l","kind":"fog","params":{"speed":"0.5"}}]}}""", "quoted.json");

            assertEquals(new ParamValue.Text("0.5"),
                    binding.stack().layers().get(0).params().get("speed").orElseThrow());
        }

        @Test
        @DisplayName("malformed JSON is a pack error naming the file, not a gson exception")
        void malformedJson() {
            PackException e = failing("{ not json at all");
            assertEquals("nether.json", e.source());
            assertTrue(e.problem().startsWith("not valid JSON"), e::getMessage);
        }

        @Test
        @DisplayName("a bad dimension id names the field")
        void badDimensionId() {
            PackException e = failing("{\"id\":\"b\",\"dimension\":\"a:b:c\"}");
            assertEquals("dimension", e.path());
        }

        @Test
        @DisplayName("nesting inside a condition tree still produces a usable path")
        void nestedConditionPath() {
            PackException e = failing("""
                    {"id":"b","dimension":"minecraft:overworld","condition":{
                      "type":"all","of":[{"type":"always"},
                                         {"type":"any","of":[{"type":"nope"}]}]}}""");

            assertEquals("condition.of[1].of[0].type", e.path());
        }
    }

    private static DimensionBinding bindingWith(EffectParams params) {
        return new DimensionBinding(
                "b", DimensionId.minecraft("overworld"), Condition.always(),
                EffectStack.of(EffectLayer.of("l", EffectKind.CUSTOM, params)), 0);
    }
}
