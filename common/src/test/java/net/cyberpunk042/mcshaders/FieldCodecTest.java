package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import net.cyberpunk042.mcshaders.codec.FieldCodec;
import net.cyberpunk042.mcshaders.codec.PackException;
import net.cyberpunk042.mcshaders.core.animation.Animation;
import net.cyberpunk042.mcshaders.core.appearance.Appearance;
import net.cyberpunk042.mcshaders.core.field.FieldLayer;
import net.cyberpunk042.mcshaders.core.field.Primitive;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;
import net.cyberpunk042.mcshaders.core.shape.RingShape;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The field codec, against the two layers the docs put in front of a reader.
 *
 * <p>Round-tripping is the property worth testing here rather than any particular
 * spelling: both directions are derived from the same annotations, so an inequality
 * after a write and a read is the only way a directive can be wrong in a way that
 * matters — a value omitted for a reason the reader does not honour comes back
 * different, and nothing else about the file has to be inspected to catch it.
 */
class FieldCodecTest {

    private static Primitive primitive(String id, Shape shape, Appearance look, Animation move) {
        return SimplePrimitive.of(id, shape.getType(), shape)
                .withAppearance(look).withAnimation(move);
    }

    private static FieldLayer sun() {
        return FieldLayer.of("sun", List.of(
                primitive("core", SphereShape.of(1.0f),
                        Appearance.glowing("#ffd27f", 0.9f), Animation.pulse(0.06f, 0.4f)),
                primitive("corona", RaysShape.EMISSION,
                        Appearance.glowing("#ff9c3f", 1.0f), Animation.spin(0.01f))));
    }

    private static FieldLayer rings() {
        return FieldLayer.of("magic_circle", List.of(
                primitive("outer", RingShape.at(1.40f, 1.50f, 0f),
                        Appearance.glowing("#8fd3ff", 0.8f), Animation.spin(0.02f)),
                primitive("inner", RingShape.at(0.70f, 0.78f, 0f),
                        Appearance.glowing("#c9a2ff", 0.8f), Animation.spin(-0.035f))));
    }

    @Nested
    class RoundTrip {

        @Test
        @DisplayName("a sun survives being written out and read back")
        void sunRoundTrips() {
            FieldLayer original = sun();
            FieldLayer returned = FieldCodec.read(FieldCodec.write(original).toString(), "sun");
            assertEquals(original, returned);
        }

        @Test
        @DisplayName("so do counter-turning rings")
        void ringsRoundTrip() {
            FieldLayer original = rings();
            FieldLayer returned = FieldCodec.read(FieldCodec.write(original).toString(), "rings");
            assertEquals(original, returned);
        }

        @Test
        @DisplayName("every shape in the catalogue survives the trip")
        void everyShapeRoundTrips() {
            for (String name : net.cyberpunk042.mcshaders.core.shape.ShapeRegistry.names()) {
                Shape shape = net.cyberpunk042.mcshaders.core.shape.ShapeRegistry
                        .create(name, java.util.Map.of());
                FieldLayer layer = FieldLayer.of("l", List.of(
                        SimplePrimitive.of("p", shape.getType(), shape)));
                FieldLayer back = FieldCodec.read(FieldCodec.write(layer).toString(), name);
                assertEquals(layer, back, name + " did not survive the round trip");
            }
        }
    }

    @Nested
    class Omission {

        @Test
        @DisplayName("a value equal to its constant is left out, and comes back anyway")
        void defaultsAreOmittedAndRestored() {
            // transform, visibility, arrangement and link are all at their constants here,
            // and each is annotated as skippable for exactly that reason.
            FieldLayer layer = FieldLayer.of("l", List.of(
                    SimplePrimitive.of("p", "sphere", SphereShape.of(1.0f))));
            JsonObject written = FieldCodec.write(layer);
            JsonObject primitive = written.getAsJsonArray("primitives").get(0).getAsJsonObject();

            assertFalse(primitive.has("transform"), "an identity transform should not be written");
            assertFalse(primitive.has("link"), "an absent link should not be written");
            assertEquals(layer, FieldCodec.read(written.toString(), "l"),
                    "what was omitted did not come back");
        }

        @Test
        @DisplayName("a value that differs from its constant is written")
        void nonDefaultsSurvive() {
            FieldLayer layer = FieldLayer.of("l", List.of(
                    SimplePrimitive.of("p", "sphere", SphereShape.of(1.0f))
                            .withAnimation(Animation.spin(0.25f))));
            JsonObject primitive = FieldCodec.write(layer)
                    .getAsJsonArray("primitives").get(0).getAsJsonObject();
            assertTrue(primitive.has("animation"), "a real animation should be written");
        }
    }

    @Nested
    class Errors {

        @Test
        @DisplayName("a shape with no type is refused where it is, not later")
        void shapeNeedsItsType() {
            PackException e = assertThrows(PackException.class, () -> FieldCodec.read("""
                    { "id": "l", "primitives": [ { "id": "p", "shape": { "radius": 1.0 } } ] }""",
                    "bad.json"));
            assertTrue(e.getMessage().contains("type"), e.getMessage());
            assertTrue(e.getMessage().contains("bad.json"), e.getMessage());
        }

        @Test
        @DisplayName("an unknown shape type lists the ones there are")
        void unknownShapeTypeIsNamed() {
            PackException e = assertThrows(PackException.class, () -> FieldCodec.read("""
                    { "id": "l", "primitives": [
                      { "id": "p", "type": "pyramid", "shape": {} } ] }""", "bad.json"));
            assertTrue(e.getMessage().contains("pyramid"), e.getMessage());
            assertTrue(e.getMessage().contains("sphere"), "should list the real ones: " + e.getMessage());
        }

        @Test
        @DisplayName("malformed JSON is reported as that, with the source")
        void malformedJson() {
            PackException e = assertThrows(PackException.class,
                    () -> FieldCodec.read("{ not json", "broken.json"));
            assertTrue(e.getMessage().contains("broken.json"), e.getMessage());
        }
    }
}
