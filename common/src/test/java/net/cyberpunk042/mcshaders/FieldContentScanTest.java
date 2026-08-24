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

    private record Tally(int files, int read, Map<String, Integer> causes) {
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
        out.append(String.format("%-20s %-18s %2d/%2d read%n",
                dir, slot, tally.read(), tally.files()));
        tally.causes().entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(cause -> out.append("      ").append(cause.getValue())
                        .append("x  ").append(cause.getKey()).append('\n'));
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
            return new Tally(0, 0, Map.of());
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> listing = Files.list(dir)) {
            listing.filter(path -> path.toString().endsWith(".json")).sorted().forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        int read = 0;
        Map<String, Integer> causes = new LinkedHashMap<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                JsonElement json = JsonParser.parseString(Files.readString(file));
                FieldCodec.read(wrapper.wrap(json), name);
                read++;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (RuntimeException e) {
                causes.merge(reason(e), 1, Integer::sum);
            }
        }
        return new Tally(files.size(), read, causes);
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
