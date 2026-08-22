package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import net.cyberpunk042.mcshaders.core.schema.SchemaRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The link between an effect being registered and its being editable.
 *
 * <p>The gap this closes was that there was none: an effect definition says what an
 * effect is and what its values default to, never which of them a person may change.
 * A mod could register an effect that no editor could ever show, and nothing anywhere
 * would report a problem — the editor would simply have nothing to open.
 */
class SchemaRegistryTest {

    private static EffectSchema schema(String name) {
        return EffectSchema.builder(name, name.toLowerCase(java.util.Locale.ROOT), 1)
                .group("Core", ParamSpec.slider("size", "Size", 0, 1, 0.5, "Core"))
                .build();
    }

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("a registered type becomes editable and its schema is findable")
        void registeredTypeIsEditable() {
            SchemaRegistry registry = new SchemaRegistry();
            registry.register("orb", schema("Orb"));

            assertTrue(registry.isEditable("orb"));
            assertEquals("Orb", registry.forType("orb").orElseThrow().displayName());
        }

        @Test
        @DisplayName("a type nobody registered is not editable, and says so quietly")
        void unregisteredTypeIsNotEditable() {
            // Empty rather than throwing: not every effect has anything worth tuning,
            // and an editor asking about one should get "no" rather than an exception.
            SchemaRegistry registry = new SchemaRegistry();

            assertFalse(registry.isEditable("nobody"));
            assertTrue(registry.forType("nobody").isEmpty());
        }

        @Test
        @DisplayName("a second schema for one type is refused rather than winning")
        void duplicateTypeIsRefused() {
            // Last-write-wins here would show the wrong controls for somebody else's
            // effect, which reads as a broken editor rather than as a mod conflict.
            SchemaRegistry registry = new SchemaRegistry();
            registry.register("orb", schema("Orb"));

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> registry.register("orb", schema("Other")));
            assertTrue(thrown.getMessage().contains("orb"), thrown.getMessage());
        }

        @Test
        @DisplayName("null arguments are refused where they are passed")
        void nullsAreRefused() {
            SchemaRegistry registry = new SchemaRegistry();

            assertThrows(IllegalArgumentException.class, () -> registry.register(null, schema("X")));
            assertThrows(IllegalArgumentException.class, () -> registry.register("", schema("X")));
            assertThrows(IllegalArgumentException.class, () -> registry.register("x", null));
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("registering after the freeze is refused, and the message says why")
        void lateRegistrationIsRefused() {
            SchemaRegistry registry = new SchemaRegistry();
            registry.freeze();

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> registry.register("late", schema("Late")));
            assertTrue(thrown.getMessage().contains("initialisation"),
                    "the error does not say when registration should have happened: "
                            + thrown.getMessage());
        }

        @Test
        @DisplayName("freezing twice is fine")
        void freezeIsIdempotent() {
            SchemaRegistry registry = new SchemaRegistry();
            registry.freeze();
            registry.freeze();
            assertTrue(registry.isFrozen());
        }

        @Test
        @DisplayName("reading still works after the freeze")
        void readingSurvivesTheFreeze() {
            SchemaRegistry registry = new SchemaRegistry();
            registry.register("orb", schema("Orb"));
            registry.freeze();

            assertTrue(registry.isEditable("orb"));
            assertEquals(1, registry.size());
        }
    }

    @Nested
    @DisplayName("order")
    class Order {

        @Test
        @DisplayName("types come back in the order they were registered")
        void registrationOrderIsPreserved() {
            // An editor lists these. Copying them through Map.copyOf gives a map whose
            // iteration order is unspecified — which holds in a small test and stops
            // holding once there are enough entries to change the hashing, so the list
            // shuffles between runs and nothing reports it.
            SchemaRegistry registry = new SchemaRegistry();
            List<String> registered = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                String type = "effect_" + i;
                registry.register(type, schema("Effect " + i));
                registered.add(type);
            }

            assertEquals(registered, registry.editableTypes(),
                    "the registry reordered its types");
            assertEquals(registered.size(), registry.all().size());
            assertEquals("Effect 0", registry.all().get(0).displayName());
            assertEquals("Effect 39", registry.all().get(39).displayName());
        }

        @Test
        @DisplayName("what comes out cannot be changed by the caller")
        void listsAreUnmodifiable() {
            SchemaRegistry registry = new SchemaRegistry();
            registry.register("orb", schema("Orb"));

            assertThrows(UnsupportedOperationException.class,
                    () -> registry.editableTypes().add("sneaked-in"));
        }
    }
}
