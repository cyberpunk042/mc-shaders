package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.backend.BackendCapabilities;
import net.cyberpunk042.mcshaders.core.backend.BackendFactory;
import net.cyberpunk042.mcshaders.core.backend.BackendRegistry;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.backend.NoOpBackend;
import net.cyberpunk042.mcshaders.core.effect.EffectDefinition;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectRegistry;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.graph.EffectCompiler;
import net.cyberpunk042.mcshaders.core.graph.EffectGraph;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Covers the extension points third-party mods build against. */
class ExtensionTest {

    private static final String KALEIDOSCOPE = "othermod:kaleidoscope";

    private static EffectDefinition kaleidoscope() {
        return EffectDefinition.of(KALEIDOSCOPE, "othermod")
                .withDefaults(EffectParams.builder().scalar("segments", 6.0).build());
    }

    /** A factory whose behaviour each test dictates. */
    private record TestFactory(
            String id, int priority, boolean available,
            boolean throwOnCreate, boolean throwOnInit, boolean initSucceeds)
            implements BackendFactory {

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public EffectBackend create() {
            if (throwOnCreate) {
                throw new IllegalStateException("create() blew up");
            }
            return new TestBackend(id, throwOnInit, initSucceeds);
        }

        static TestFactory ok(String id, int priority) {
            return new TestFactory(id, priority, true, false, false, true);
        }
    }

    private static final class TestBackend implements EffectBackend {
        private final String id;
        private final boolean throwOnInit;
        private final boolean initSucceeds;
        boolean closed;

        TestBackend(String id, boolean throwOnInit, boolean initSucceeds) {
            this.id = id;
            this.throwOnInit = throwOnInit;
            this.initSucceeds = initSucceeds;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public BackendCapabilities capabilities() {
            return BackendCapabilities.full(id);
        }

        @Override
        public boolean initialise() {
            if (throwOnInit) {
                throw new IllegalStateException("initialise() blew up");
            }
            return initSucceeds;
        }

        @Override
        public void render(EffectGraph graph, FrameContext frame) {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Nested
    @DisplayName("effect definitions")
    class Definitions {

        @Test
        void typesMustBeNamespaced() {
            assertThrows(IllegalArgumentException.class, () -> EffectDefinition.of("bare", "mod"));
            assertThrows(IllegalArgumentException.class, () -> EffectDefinition.of("a:b:c", "mod"));
            assertThrows(IllegalArgumentException.class, () -> EffectDefinition.of(":name", "mod"));
            assertThrows(IllegalArgumentException.class, () -> EffectDefinition.of("ns:", "mod"));
            assertEquals("othermod", kaleidoscope().namespace());
        }

        @Test
        @DisplayName("supporting CUSTOM generally is not enough to render a specific custom effect")
        void customKindRequiresTheExactType() {
            EffectDefinition def = kaleidoscope();
            assertFalse(def.isRenderableBy(Set.of(EffectKind.CUSTOM), Set.of(), true),
                    "a backend that knows CUSTOM but not this type cannot render it");
            assertTrue(def.isRenderableBy(Set.of(EffectKind.CUSTOM), Set.of(KALEIDOSCOPE), true));
        }

        @Test
        void depthRequirementIsHonoured() {
            EffectDefinition def = kaleidoscope().requiringDepth();
            assertFalse(def.isRenderableBy(Set.of(EffectKind.CUSTOM), Set.of(KALEIDOSCOPE), false));
            assertTrue(def.isRenderableBy(Set.of(EffectKind.CUSTOM), Set.of(KALEIDOSCOPE), true));
        }
    }

    @Nested
    @DisplayName("effect registry lifecycle")
    class Effects {

        @Test
        @DisplayName("a second mod cannot silently shadow an existing type")
        void duplicateTypesAreRefused() {
            EffectRegistry registry = new EffectRegistry().register(kaleidoscope());
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> registry.register(EffectDefinition.of(KALEIDOSCOPE, "thirdmod")));
            assertTrue(e.getMessage().contains("othermod"), "the error should name the current owner");
        }

        @Test
        void registrationClosesOnFreeze() {
            EffectRegistry registry = new EffectRegistry();
            registry.freeze();
            assertTrue(registry.isFrozen());
            assertThrows(IllegalStateException.class, () -> registry.register(kaleidoscope()));
            registry.freeze(); // idempotent
        }

        @Test
        @DisplayName("definition defaults fill gaps without overriding the pack")
        void defaultsDoNotOverrideExplicitValues() {
            EffectRegistry registry = new EffectRegistry().register(kaleidoscope());

            EffectLayer explicit = EffectLayer.builder("k")
                    .definition(kaleidoscope())
                    .params(EffectParams.builder().scalar("segments", 12.0).build())
                    .build();
            EffectLayer bare = EffectLayer.builder("k").definition(kaleidoscope()).build();

            assertEquals(12.0, registry.applyDefaults(explicit).params().scalarOr("segments", -1));
            assertEquals(6.0, registry.applyDefaults(bare).params().scalarOr("segments", -1));
        }

        @Test
        void layersNamingNoDefinitionPassThroughUntouched() {
            EffectRegistry registry = new EffectRegistry();
            EffectLayer plain = EffectLayer.of("fog", EffectKind.FOG, EffectParams.empty());
            assertEquals(plain, registry.applyDefaults(plain));
        }
    }

    @Nested
    @DisplayName("backend selection")
    class Backends {

        @Test
        void highestPriorityAvailableBackendWins() {
            BackendRegistry registry = new BackendRegistry()
                    .register(TestFactory.ok("low", 0))
                    .register(TestFactory.ok("high", 100));

            assertEquals("high", registry.select().id());
        }

        @Test
        void unavailableBackendsAreSkipped() {
            BackendRegistry registry = new BackendRegistry()
                    .register(new TestFactory("high", 100, false, false, false, true))
                    .register(TestFactory.ok("low", 0));

            List<String> skipped = new ArrayList<>();
            assertEquals("low", registry.select(skipped::add).id());
            assertTrue(skipped.stream().anyMatch(m -> m.contains("high")));
        }

        @Test
        @DisplayName("a backend that probes fine but fails to initialise falls through")
        void failedInitialisationFallsThrough() {
            BackendRegistry registry = new BackendRegistry()
                    .register(new TestFactory("high", 100, true, false, false, false))
                    .register(TestFactory.ok("low", 0));

            assertEquals("low", registry.select().id());
        }

        @Test
        @DisplayName("a third party's factory throwing must not take the game down")
        void throwingFactoriesAreContained() {
            BackendRegistry registry = new BackendRegistry()
                    .register(new TestFactory("throws-create", 200, true, true, false, true))
                    .register(new TestFactory("throws-init", 100, true, false, true, true))
                    .register(TestFactory.ok("safe", 0));

            List<String> skipped = new ArrayList<>();
            assertEquals("safe", registry.select(skipped::add).id());
            assertEquals(2, skipped.size(), "both throwing candidates should be reported");
        }

        @Test
        @DisplayName("selection never returns null, even with nothing usable")
        void fallsBackToNoOp() {
            EffectBackend selected = new BackendRegistry().select();
            assertInstanceOf(NoOpBackend.class, selected);
        }

        @Test
        void selectionOrderIsDeterministicOnTies() {
            BackendRegistry a = new BackendRegistry()
                    .register(TestFactory.ok("bbb", 5)).register(TestFactory.ok("aaa", 5));
            BackendRegistry b = new BackendRegistry()
                    .register(TestFactory.ok("aaa", 5)).register(TestFactory.ok("bbb", 5));

            assertEquals(a.select().id(), b.select().id(),
                    "equal priorities must not depend on registration order");
        }

        @Test
        void duplicateBackendIdsAreRefused() {
            BackendRegistry registry = new BackendRegistry().register(TestFactory.ok("dup", 0));
            assertThrows(IllegalStateException.class, () -> registry.register(TestFactory.ok("dup", 1)));
        }

        @Test
        void registrationClosesOnFreeze() {
            BackendRegistry registry = new BackendRegistry();
            registry.freeze();
            assertThrows(IllegalStateException.class, () -> registry.register(TestFactory.ok("late", 0)));
        }
    }

    @Nested
    @DisplayName("compiling third-party effects")
    class Compilation {

        private EffectStack stackWith(EffectLayer layer) {
            return EffectStack.of(layer);
        }

        @Test
        @DisplayName("a missing provider mod degrades to a warning, not a crash")
        void unregisteredTypesAreSkippedWithAnExplanation() {
            EffectCompiler compiler = new EffectCompiler(
                    BackendCapabilities.full("test").withTypes(Set.of(KALEIDOSCOPE)),
                    new EffectRegistry());

            EffectGraph graph = compiler.compile(stackWith(
                    EffectLayer.builder("k").type(KALEIDOSCOPE).kind(EffectKind.CUSTOM).build()));

            assertTrue(graph.isEmpty());
            assertTrue(graph.warnings().get(0).contains("not registered"));
        }

        @Test
        void backendsThatDoNotImplementATypeSkipIt() {
            EffectRegistry effects = new EffectRegistry().register(kaleidoscope());
            EffectCompiler compiler = new EffectCompiler(BackendCapabilities.full("test"), effects);

            EffectGraph graph = compiler.compile(stackWith(
                    EffectLayer.builder("k").definition(kaleidoscope()).build()));

            assertTrue(graph.isEmpty(), "full() supports CUSTOM but declares no custom types");
            assertTrue(graph.warnings().get(0).contains("does not implement effect type"));
        }

        @Test
        void aSupportedThirdPartyEffectCompilesWithItsDefaults() {
            EffectRegistry effects = new EffectRegistry().register(kaleidoscope());
            EffectCompiler compiler = new EffectCompiler(
                    BackendCapabilities.full("test").withTypes(Set.of(KALEIDOSCOPE)), effects);

            EffectGraph graph = compiler.compile(stackWith(
                    EffectLayer.builder("k").definition(kaleidoscope()).build()));

            assertEquals(1, graph.passCount());
            assertTrue(graph.warnings().isEmpty());
            assertEquals(KALEIDOSCOPE, graph.nodes().get(0).type());
            assertTrue(graph.nodes().get(0).hasDefinitionType());
            assertEquals(6.0, graph.nodes().get(0).params().scalarOr("segments", -1),
                    "the definition's defaults should have been applied");
        }

        @Test
        void builtInEffectsAreUnaffectedByTheRegistry() {
            EffectCompiler compiler = new EffectCompiler(BackendCapabilities.full("test"));
            EffectGraph graph = compiler.compile(stackWith(
                    EffectLayer.of("fog", EffectKind.FOG, EffectParams.empty())));

            assertEquals(1, graph.passCount());
            assertFalse(graph.nodes().get(0).hasDefinitionType());
        }
    }
}
