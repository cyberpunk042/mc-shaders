package net.cyberpunk042.mcshaders.core.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The shape registry, run for the first time.
 *
 * <p>It arrived with the port from the-virus-block-mc carrying a worked usage example
 * in its class javadoc — {@code ShapeRegistry.create("sphere", 1.0f)} and a parameter
 * map — and until now <strong>nothing in this repository had called any of it.</strong>
 * Not main code, not another test. A 400-line registry whose documented entry points
 * had never been executed: if {@code create} threw on every name, or the built-in
 * shapes had never registered themselves, every build would still have been green.
 *
 * <p>Found by asking which public types are referenced by no other file at all. That
 * question turned up 21, most of them genuinely waiting on the field system. This one
 * was different: it has a public API with documented usage, so it is either worth
 * exercising or worth removing, and it cannot be neither.
 *
 * <p>The tests below are deliberately about the <em>contract</em> — that names resolve,
 * that unknown ones fail loudly, that parameters reach the shape — rather than about
 * particular geometry, which {@code ShapeMath} and the per-shape tests already cover.
 */
class ShapeRegistryTest {

    @Nested
    @DisplayName("what it knows about")
    class Names {

        @Test
        @DisplayName("the built-in shapes registered themselves, rather than an empty registry")
        void builtinsArePresent() {
            // The failure this guards: a registry whose static initialiser never ran,
            // or ran and registered nothing. Both look identical to a caller — every
            // create() throws — and neither would fail a build.
            Set<String> names = ShapeRegistry.names();

            assertFalse(names.isEmpty(), "no shape ever registered itself");
            assertTrue(names.contains("sphere"), "sphere is the javadoc's own example: " + names);
        }

        @Test
        @DisplayName("count agrees with names, rather than being a separate tally")
        void countMatchesNames() {
            assertEquals(ShapeRegistry.names().size(), ShapeRegistry.count());
        }

        @Test
        @DisplayName("exists() answers for a name it knows and one it does not")
        void existsAnswersBothWays() {
            assertTrue(ShapeRegistry.exists("sphere"));
            assertFalse(ShapeRegistry.exists("no_such_shape_anywhere"));
        }
    }

    @Nested
    @DisplayName("creating a shape")
    class Creating {

        @Test
        @DisplayName("the javadoc's own example runs")
        void javadocExampleRuns() {
            // Verbatim from the class javadoc. It had never been executed.
            Shape sphere = ShapeRegistry.create("sphere", 1.0f);

            assertNotNull(sphere);
        }

        @Test
        @DisplayName("every registered name can actually be created, not just listed")
        void everyNameResolves() {
            // Listing a name and being able to build it are different claims, and a
            // registry is exactly where they drift apart.
            for (String name : ShapeRegistry.names()) {
                assertNotNull(ShapeRegistry.create(name), "listed but not creatable: " + name);
            }
        }

        @Test
        @DisplayName("an unknown name returns null, which is the documented answer")
        void unknownNameIsNull() {
            assertNull(ShapeRegistry.create("no_such_shape_anywhere"));
        }

        @Test
        @DisplayName("a factory that throws surfaces, rather than becoming another null")
        void failingFactorySurfaces() {
            // The bug this pins. create() caught every exception and returned null, so a
            // bad parameter and an unknown name gave the identical answer — and the port
            // had already removed the log lines that once told them apart. A caller had
            // no way to learn anything had gone wrong at all.
            ShapeRegistry.register("test_registry_throws", params -> {
                throw new IllegalStateException("factory refused these parameters");
            });

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> ShapeRegistry.create("test_registry_throws"));
            assertEquals("factory refused these parameters", thrown.getMessage(),
                    "the factory's own reason should reach the caller");
        }

        @Test
        @DisplayName("a null name is refused rather than becoming a NullPointerException")
        void nullNameRefused() {
            assertThrows(IllegalArgumentException.class, () -> ShapeRegistry.create(null));
        }
    }

    @Nested
    @DisplayName("registering your own")
    class Registering {

        @Test
        @DisplayName("a registered supplier is findable and creatable afterwards")
        void registerSimpleRoundTrips() {
            ShapeRegistry.registerSimple("test_registry_roundtrip",
                    () -> ShapeRegistry.create("sphere", 1.0f));

            assertTrue(ShapeRegistry.exists("test_registry_roundtrip"));
            assertNotNull(ShapeRegistry.create("test_registry_roundtrip"));
        }

        @Test
        @DisplayName("a factory receives the parameter map it was given")
        void factoryReceivesParams() {
            // The parameter-map overload is the half of the javadoc example that does
            // more than name a shape, so it is the half worth pinning.
            Map<String, Object> seen = new java.util.HashMap<>();
            ShapeRegistry.register("test_registry_params", params -> {
                seen.putAll(params);
                return ShapeRegistry.create("sphere", 1.0f);
            });

            ShapeRegistry.create("test_registry_params", Map.of("radius", 2.0f, "segments", 48));

            assertEquals(2.0f, seen.get("radius"));
            assertEquals(48, seen.get("segments"));
        }
    }
}
