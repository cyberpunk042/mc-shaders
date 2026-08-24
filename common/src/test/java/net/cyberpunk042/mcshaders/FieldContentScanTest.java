package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.cyberpunk042.mcshaders.codec.FieldCodec;
import net.cyberpunk042.mcshaders.core.field.FieldLayer;
import net.cyberpunk042.mcshaders.core.field.LayerGeometry;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Re-derives the numbers in {@code docs/VIRUS-BLOCK-FIELD-STATE.md}.
 *
 * <p>{@code docs/PORTING.md} requires that "the engine must be able to load content it
 * does not contain". The shader half of that has {@code mcshaders-check}. This is the
 * field half, and it is deliberately less than a command-line tool: where a shipped one
 * would live is an open question, because {@code check/} is an included build so a pack
 * can be validated without a Minecraft toolchain, {@code FieldCodec} lives here in a
 * subproject, and an included build cannot depend on a subproject. A test needs none of
 * that resolved.
 *
 * <p>Point it at an unpacked content tree:
 *
 * <pre>{@code
 * ./gradlew :common:test --tests '*FieldContentScanTest' \
 *     -Dmcshaders.fieldContent=../the-virus-block-mc/config/the-virus-block
 * }</pre>
 *
 * <p>Skipped when that property is absent, which is every CI run — the content is not in
 * this repository and must not be. It is CC-licensed where this repository is MIT, and
 * {@code PORTING.md} calls embedding one in the other "a design error, not a packaging
 * detail".
 *
 * <h2>Two steps, counted separately</h2>
 *
 * <p>A file is counted <em>read</em> when {@link FieldCodec} parses it and <em>built</em>
 * when {@link LayerGeometry} turns the result into pieces. They are separate columns
 * because they fail for unrelated reasons — an unknown JSON key and a shape with no
 * tessellator are different problems — and reporting one number would hide whichever
 * came second.
 *
 * <p><em>empty</em> is the third column and the reason the second is not enough: a build
 * that succeeds and produces nothing but empty meshes is, from the outside,
 * indistinguishable from one that worked. That is the same failure this scan already
 * refuses to commit about paths, applied one stage later.
 *
 * <p>What none of the three columns can see is a <em>substitution</em>. The
 * {@code TYPE_A} and {@code TYPE_E} sphere algorithms have no mesh form, so the
 * tessellator logs a fallback and returns a lat-lon sphere instead: non-empty, no
 * exception, different shape. Counting that as built is honest about what was measured
 * and misleading about what happened, so it is written down in
 * {@code docs/VIRUS-BLOCK-FIELD-STATE.md} rather than inferred from a log line. Reading
 * the log back here would tie this scan to a logging backend to buy one number, which
 * is not a trade worth making for a tool whose whole point is that it is small.
 *
 * <h2>It asserts that it looked, not what it found</h2>
 *
 * <p>The counts are a property of somebody else's tree and change when they edit it, so
 * failing on a number would make this a tripwire on a repository we do not control. What
 * it does fail on is finding nothing: a wrong path silently reporting zero everywhere is
 * exactly how the first runs of this scan lied to me, and a scan that cannot tell
 * "unreadable" from "not there" is worse than no scan.
 */
class FieldContentScanTest {

    private static final String PROPERTY = "mcshaders.fieldContent";

    /** Each content directory, and the layer slot its files belong in. */
    private static final Map<String, String> SLOTS = new LinkedHashMap<>();

    static {
        SLOTS.put("field_appearances", "appearance");
        SLOTS.put("field_animations", "animation");
        SLOTS.put("field_fills", "fill");
        SLOTS.put("field_masks", "visibility");
        SLOTS.put("field_arrangements", "arrangement");
        SLOTS.put("field_links", "link");
    }

    /** A layer the codec wrote, so a failure is never this test's own carrier. */
    private static JsonObject carrier() {
        return FieldCodec.write(FieldLayer.of("scan", List.of(
                SimplePrimitive.of("p", SphereShape.of(1.0f).getType(), SphereShape.of(1.0f)))));
    }

    private record Tally(int files, int read, int built, int empty,
            Map<String, Integer> readCauses, Map<String, Integer> buildCauses) {
    }

    @Test
    @EnabledIfSystemProperty(named = PROPERTY, matches = ".+")
    @DisplayName("scan a content tree and report what reads")
    void scan() {
        Path root = Path.of(System.getProperty(PROPERTY));
        assertTrue(Files.isDirectory(root), () -> root + " is not a directory");

        StringBuilder report = new StringBuilder("\nfield content scan: " + root + "\n\n");
        int seenAnywhere = 0;

        Tally shapes = tallyShapes(root.resolve("field_shapes"));
        seenAnywhere += shapes.files();
        append(report, "field_shapes", "(shape document)", shapes);

        for (Map.Entry<String, String> slot : SLOTS.entrySet()) {
            Tally tally = tallySlot(root.resolve(slot.getKey()), slot.getValue());
            seenAnywhere += tally.files();
            append(report, slot.getKey(), slot.getValue(), tally);
        }

        System.out.println(report);

        int found = seenAnywhere;
        assertTrue(found > 0,
                () -> "no .json files under " + root + " — a scan that finds nothing and says "
                        + "nothing is indistinguishable from one that found everything "
                        + "unreadable. Check the path.");
    }

    private static void append(StringBuilder out, String dir, String slot, Tally tally) {
        out.append(String.format("%-20s %-18s %2d/%2d read   %2d built   %2d empty%n",
                dir, slot, tally.read(), tally.files(), tally.built(), tally.empty()));
        causes(out, "read", tally.readCauses());
        causes(out, "build", tally.buildCauses());
    }

    private static void causes(StringBuilder out, String step, Map<String, Integer> causes) {
        causes.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(cause -> out.append("      ").append(cause.getValue())
                        .append("x  ").append(step).append(": ").append(cause.getKey())
                        .append('\n'));
    }

    /** A {@code field_shapes} file is a shape, wrapped in a primitive that carries it. */
    private static Tally tallyShapes(Path dir) {
        return tally(dir, json -> {
            JsonObject layer = carrier();
            JsonObject primitive = layer.getAsJsonArray("primitives").get(0).getAsJsonObject();
            JsonElement type = json.isJsonObject() ? json.getAsJsonObject().get("type") : null;
            if (type != null) {
                primitive.add("type", type);
            }
            primitive.add("shape", json);
            return layer.toString();
        });
    }

    /** Every other directory holds one component of a primitive. */
    private static Tally tallySlot(Path dir, String slot) {
        return tally(dir, json -> {
            JsonObject layer = carrier();
            layer.getAsJsonArray("primitives").get(0).getAsJsonObject().add(slot, json);
            return layer.toString();
        });
    }

    private interface Wrapper {
        String wrap(JsonElement json);
    }

    private static Tally tally(Path dir, Wrapper wrapper) {
        if (!Files.isDirectory(dir)) {
            return new Tally(0, 0, 0, 0, Map.of(), Map.of());
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> listing = Files.list(dir)) {
            listing.filter(path -> path.toString().endsWith(".json")).sorted().forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        int read = 0;
        int built = 0;
        int empty = 0;
        Map<String, Integer> readCauses = new LinkedHashMap<>();
        Map<String, Integer> buildCauses = new LinkedHashMap<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            FieldLayer layer;
            try {
                JsonElement json = JsonParser.parseString(Files.readString(file));
                layer = FieldCodec.read(wrapper.wrap(json), name);
                read++;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (RuntimeException e) {
                readCauses.merge(reason(e), 1, Integer::sum);
                continue;
            }
            try {
                // IGNORE, because a scan measures content and must not decide it: any
                // other policy would be this test inventing how radiusMatch resizes.
                List<LayerGeometry.Piece> pieces =
                        LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);
                built++;
                if (pieces.stream().allMatch(piece -> piece.mesh().isEmpty())) {
                    empty++;
                }
            } catch (RuntimeException e) {
                buildCauses.merge(reason(e), 1, Integer::sum);
            }
        }
        return new Tally(files.size(), read, built, empty, readCauses, buildCauses);
    }

    /** The message without the file name, so identical causes group instead of listing. */
    private static String reason(RuntimeException e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        return message.replaceAll("^[^:]*: ", "");
    }
}
