package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.mesh.Mesh;
import net.cyberpunk042.mcshaders.core.mesh.Tessellator;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.pattern.EdgePattern;
import net.cyberpunk042.mcshaders.core.pattern.QuadPattern;
import net.cyberpunk042.mcshaders.core.pattern.SectorPattern;
import net.cyberpunk042.mcshaders.core.pattern.SegmentPattern;
import net.cyberpunk042.mcshaders.core.pattern.TrianglePattern;
import net.cyberpunk042.mcshaders.core.shape.ShapeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every name the shape registry offers produces geometry through the dispatcher.
 *
 * <p>{@code TessellationTest} calls each tessellator directly, which is why six of them
 * could be missing from {@link Tessellator}'s switch while every one of them passed its
 * tests. Creating a {@code rays} by name and tessellating it returned an empty mesh and
 * a log line — a shape the registry advertises, a tessellator that works, and nothing
 * joining them.
 *
 * <p>The list is taken from {@link ShapeRegistry#names()} rather than written out here,
 * so registering a shape without dispatching it fails this test instead of drawing
 * nothing in someone's world.
 */
class DispatcherCoverageTest {

    @Test
    @DisplayName("every registered shape name tessellates to real geometry")
    void everyRegisteredNameReachesATessellator() {
        List<String> names = new ArrayList<>(ShapeRegistry.names());
        assertTrue(names.size() >= 12,
                "expected the registry to offer a substantial catalogue, found " + names);

        List<String> empty = new ArrayList<>();
        List<String> uncreatable = new ArrayList<>();
        for (String name : names) {
            Shape shape = ShapeRegistry.create(name, Map.of());
            if (shape == null) {
                uncreatable.add(name);
                continue;
            }
            Mesh mesh = Tessellator.tessellateAuto(shape);
            if (mesh.isEmpty()) {
                empty.add(name + " (" + shape.getClass().getSimpleName() + ")");
            }
        }

        assertTrue(uncreatable.isEmpty(),
                "the registry lists these but will not build one: " + uncreatable);
        assertTrue(empty.isEmpty(),
                "these are registered and tessellate to nothing, so they are advertised "
                        + "and undrawable: " + empty);
    }

    @Test
    @DisplayName("the catalogue page names every shape the registry offers")
    void shapesPageMatchesTheRegistry() {
        // The page's table is the first thing a reader counts on. Deriving the check
        // from the registry means adding a shape and forgetting the page fails here,
        // rather than leaving a catalogue that quietly understates what exists.
        String page = readDoc("docs/SHAPES.md");
        List<String> missing = new ArrayList<>();
        for (String name : ShapeRegistry.names()) {
            if (!page.contains("`" + name + "`")) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(),
                "these shapes are registered and absent from docs/SHAPES.md: " + missing);
        assertTrue(page.contains(ShapeRegistry.names().size() + " shapes"),
                "the page's shape count is not " + ShapeRegistry.names().size());

        int patterns = QuadPattern.values().length + SegmentPattern.values().length
                + SectorPattern.values().length + EdgePattern.values().length
                + TrianglePattern.values().length;
        assertTrue(page.contains(patterns + " patterns"),
                "the page's pattern count is not " + patterns);
    }

    private static String readDoc(String name) {
        for (java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
                dir != null; dir = dir.getParent()) {
            java.nio.file.Path candidate = dir.resolve(name);
            if (java.nio.file.Files.isRegularFile(candidate)) {
                try {
                    return java.nio.file.Files.readString(candidate);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
        }
        throw new AssertionError("could not find " + name);
    }

    @Test
    @DisplayName("the geometry each name produces is indexable, not merely non-empty")
    void theGeometryIsWellFormed() {
        List<String> broken = new ArrayList<>();
        for (String name : ShapeRegistry.names()) {
            Shape shape = ShapeRegistry.create(name, Map.of());
            assertNotNull(shape, name + " could not be created");
            Mesh mesh = Tessellator.tessellateAuto(shape);
            for (int index : mesh.indices()) {
                if (index < 0 || index >= mesh.vertexCount()) {
                    broken.add(name + " indexes " + index + " of " + mesh.vertexCount());
                    break;
                }
            }
        }
        assertTrue(broken.isEmpty(), String.join("; ", broken));
    }
}
