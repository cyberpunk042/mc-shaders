package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.backend.BackendCapabilities;
import net.cyberpunk042.mcshaders.core.backend.BackendFactory;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.edit.EditSession;
import net.cyberpunk042.mcshaders.core.edit.TuningStore;
import net.cyberpunk042.mcshaders.core.effect.EffectDefinition;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.chain.ChainProblem;
import net.cyberpunk042.mcshaders.core.chain.ChainValidator;
import net.cyberpunk042.mcshaders.core.chain.Input;
import net.cyberpunk042.mcshaders.core.chain.Pass;
import net.cyberpunk042.mcshaders.core.chain.PostChain;
import net.cyberpunk042.mcshaders.core.glsl.IncludeResolver;
import net.cyberpunk042.mcshaders.core.glsl.ResolvedShader;
import net.cyberpunk042.mcshaders.core.glsl.SourceProvider;
import net.cyberpunk042.mcshaders.core.mesh.Mesh;
import net.cyberpunk042.mcshaders.core.mesh.Tessellator;
import net.cyberpunk042.mcshaders.core.shape.CylinderShape;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import net.cyberpunk042.mcshaders.core.layout.GlslBlocks;
import net.cyberpunk042.mcshaders.core.layout.GlslType;
import net.cyberpunk042.mcshaders.core.layout.LayoutComparison;
import net.cyberpunk042.mcshaders.core.layout.LayoutMismatch;
import net.cyberpunk042.mcshaders.core.layout.Std140;
import net.cyberpunk042.mcshaders.core.layout.UniformBlock;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import net.cyberpunk042.mcshaders.core.schema.SchemaAudit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Compiles and exercises the core-side examples from docs/USING_AS_A_LIBRARY.md.
 *
 * <p>A library's documentation is its contract. These examples are what a new
 * consumer copies first, so the build fails if they stop compiling or stop behaving
 * as the prose claims. Change them here and there together.
 */
class LibraryDocExampleTest {

    @Test
    @DisplayName("the dimension-look example compiles, and the conditional override merges per layer")
    void dimensionLookExample() {
        EffectStack look = EffectStack.of(
                EffectLayer.builder("haze")
                        .kind(EffectKind.DISTORT)
                        .params(EffectParams.builder().scalar("amplitude", 0.02).build())
                        .build(),
                EffectLayer.builder("grade")
                        .kind(EffectKind.COLOR_GRADE)
                        .params(EffectParams.builder().color("tint", 0.8f, 0.9f, 1.0f, 1.0f).build())
                        .build());

        DimensionId dreamscape = DimensionId.parse("mymod:dreamscape");

        BindingRegistry registry = BindingRegistry.of(
                DimensionBinding.of("mymod:dreamscape", dreamscape, look),
                new DimensionBinding(
                        "mymod:dreamscape_night",
                        dreamscape,
                        new Condition.TimeOfDay(13000, 1000),
                        EffectStack.of(EffectLayer.builder("grade").kind(EffectKind.COLOR_GRADE)
                                .params(EffectParams.builder()
                                        .color("tint", 0.3f, 0.3f, 0.6f, 1.0f).build())
                                .build()),
                        10));

        EffectStack atNight = registry.resolve(WorldState.of(dreamscape).withDayTime(18000));

        // The doc claims haze survives untouched and only grade is overridden.
        assertEquals(2, atNight.size());
        assertTrue(atNight.byId("haze").isPresent(), "the untouched layer must survive");
        assertEquals(0.3f, atNight.byId("grade").orElseThrow()
                .params().color("tint").orElseThrow().r(), 1e-6);

        EffectStack atNoon = registry.resolve(WorldState.of(dreamscape).withDayTime(6000));
        assertEquals(0.8f, atNoon.byId("grade").orElseThrow()
                .params().color("tint").orElseThrow().r(), 1e-6,
                "outside the night window the base look applies");
    }

    @Test
    @DisplayName("the custom effect example compiles, and defaults fill only the gaps")
    void customEffectExample() {
        EffectDefinition kaleidoscope = EffectDefinition.of("mymod:kaleidoscope", "mymod")
                .withDefaults(EffectParams.builder()
                        .scalar("segments", 6.0)
                        .scalar("rotation", 0.0)
                        .build());

        EffectLayer layer = EffectLayer.builder("swirl")
                .definition(kaleidoscope)
                .params(EffectParams.builder().scalar("segments", 12.0).build())
                .build();

        EffectLayer resolved = new net.cyberpunk042.mcshaders.core.effect.EffectRegistry()
                .register(kaleidoscope)
                .applyDefaults(layer);

        assertEquals(12.0, resolved.params().scalarOr("segments", -1), "explicit value wins");
        assertEquals(0.0, resolved.params().scalarOr("rotation", -1), "default fills the gap");
    }

    @Test
    @DisplayName("the backend example compiles and declares capabilities as documented")
    void backendExample() {
        BackendFactory factory = new BackendFactory() {
            @Override
            public String id() {
                return "mymod:fancy";
            }

            @Override
            public int priority() {
                return BackendFactory.DEFAULT_PRIORITY + 100;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public EffectBackend create() {
                return new RecordingBackend(new BackendCapabilities(
                        "MyGraphics 1.0",
                        Set.of(EffectKind.COLOR_GRADE, EffectKind.FOG),
                        Set.of("mymod:kaleidoscope"),
                        true,
                        8));
            }
        };

        BackendCapabilities caps = factory.create().capabilities();
        assertTrue(caps.supportsType("mymod:kaleidoscope"));
        assertTrue(caps.supports(EffectKind.FOG), "depth is available, so FOG is renderable");
        assertEquals(8, caps.maxPasses());
        assertTrue(caps.hasPassLimit());
        assertEquals(BackendFactory.DEFAULT_PRIORITY + 100, factory.priority());
    }

    @Test
    @DisplayName("the off-Minecraft usage example compiles and drives a frame")
    void standaloneUsageExample() {
        DimensionId scene = DimensionId.parse("app:scene");
        EffectStack look = EffectStack.of(
                EffectLayer.of("grade", EffectKind.COLOR_GRADE, EffectParams.empty()));

        BindingRegistry bindings = BindingRegistry.of(
                DimensionBinding.of("scene", scene, look));

        RecordingBackend backend = RecordingBackend.capable();
        ShaderPipeline pipeline = new ShaderPipeline(backend, bindings);

        WorldState state = WorldState.of(scene)
                .withDayTime(18000)
                .withWeather(Weather.RAIN);

        pipeline.snapTo(state);
        pipeline.frame(state, 1.0, new EffectBackend.FrameContext(1920, 1080, 0f, 0.0));

        assertEquals(1, backend.frameCount());
    }


    // ── docs: Working with GLSL source ──────────────────────────────────────────

    @Test
    @DisplayName("the include example resolves, and a missing include is a diagnostic not a throw")
    void includeExample() {
        Map<String, String> sources = Map.of(
                "post/tint.fsh", "#version 150\n#include \"lib/util.glsl\"\nvoid main() {}\n",
                "post/lib/util.glsl", "float half_of(float x) { return x * 0.5; }\n");
        SourceProvider files = path -> Optional.ofNullable(sources.get(path));

        ResolvedShader resolved = new IncludeResolver(files).resolve("post/tint.fsh");

        assertTrue(!resolved.hasErrors(), () -> resolved.errors().toString());
        assertTrue(resolved.source().contains("half_of"), "the include should be inlined");
        assertTrue(resolved.source().contains("#line"), "positions must map back to the original file");

        ResolvedShader broken = new IncludeResolver(
                path -> path.equals("post/tint.fsh") ? Optional.of("#include \"gone.glsl\"\n") : Optional.empty())
                .resolve("post/tint.fsh");

        assertTrue(broken.hasErrors(), "a missing include is reported, not thrown");
    }

    // ── docs: Checking a uniform block against its shader ───────────────────────

    @Test
    @DisplayName("the layout example reports the byte where the two declarations part company")
    void layoutExample() {
        String glsl = "layout(std140) uniform Config { float Radius; vec4 Tint; };";
        UniformBlock inShader = GlslBlocks.blocks(glsl).get("Config");

        UniformBlock fromHost = new UniformBlock("Config", List.of(
                new Std140.Member("Radius", GlslType.FLOAT),
                new Std140.Member("Tint", GlslType.VEC4)));

        assertTrue(LayoutComparison.errors(inShader, fromHost).isEmpty(),
                () -> LayoutComparison.compare(inShader, fromHost).toString());

        // An extra member that lands in padding the shader does not read is not a
        // divergence: Tint is still at 16 on both sides, so the shader reads correctly.
        UniformBlock intoPadding = new UniformBlock("Config", List.of(
                new Std140.Member("Radius", GlslType.FLOAT),
                new Std140.Member("Inserted", GlslType.FLOAT),
                new Std140.Member("Tint", GlslType.VEC4)));

        assertTrue(LayoutComparison.errors(inShader, intoPadding).isEmpty(),
                () -> LayoutComparison.compare(inShader, intoPadding).toString());

        // An extra member that actually shifts a later one is.
        UniformBlock shifted = new UniformBlock("Config", List.of(
                new Std140.Member("Inserted", GlslType.VEC4),
                new Std140.Member("Radius", GlslType.FLOAT),
                new Std140.Member("Tint", GlslType.VEC4)));
        List<LayoutMismatch> errors = LayoutComparison.errors(inShader, shifted);

        assertEquals(1, errors.size(), errors::toString);
        assertEquals(0, errors.get(0).offset(), "everything from byte 0 on is misread");
    }

    @Test
    @DisplayName("as the docs claim, a mat4 written as four vec4s is not reported")
    void layoutExampleIgnoresSpellingDifferences() {
        UniformBlock inShader = new UniformBlock("Camera", List.of(
                new Std140.Member("ViewProj", GlslType.MAT4)));
        UniformBlock fromHost = new UniformBlock("Camera", List.of(
                new Std140.Member("Row0", GlslType.VEC4), new Std140.Member("Row1", GlslType.VEC4),
                new Std140.Member("Row2", GlslType.VEC4), new Std140.Member("Row3", GlslType.VEC4)));

        assertTrue(LayoutComparison.agree(inShader, fromHost));
    }

    // ── docs: Validating a post-processing chain ────────────────────────────────

    @Test
    @DisplayName("the chain example catches a target read before anything wrote it")
    void chainExample() {
        String shader = "#version 150\nuniform sampler2D InSampler;\nvoid main() {}\n";
        SourceProvider files = path -> Optional.of(shader);
        PostChain chain = new PostChain(
                Map.of("swap", net.cyberpunk042.mcshaders.core.chain.TargetSpec.SCREEN_SIZED),
                List.of(new Pass("post/blit", "post/tint",
                        List.of(new Input("In", "swap")), "minecraft:main", List.of())));

        ChainValidator validator = new ChainValidator(files, Set.of("minecraft:main"));
        List<ChainProblem> problems = validator.validate(chain);

        assertTrue(problems.stream().anyMatch(p -> p.kind() == ChainProblem.Kind.READ_BEFORE_WRITE),
                problems::toString);
    }

    // ── docs: Describing what is tunable ────────────────────────────────────────

    @Test
    @DisplayName("the schema example builds, defaults, and coerces as the prose says")
    void schemaExample() {
        EffectSchema schema = EffectSchema.builder("Energy Orb", "energy_orb", 1)
                .group("Core",
                        ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"),
                        ParamSpec.toggle("core.glow", "Glow", true, "Core"))
                .group("Look",
                        ParamSpec.color("look.tint", "Tint", ParamValue.Rgba.opaque(1, 1, 1), "Look"))
                .build();

        EffectParams defaults = schema.defaults();
        assertEquals(0.15, defaults.scalarOr("core.size", -1), 1e-9);

        EffectParams edited = defaults
                .with("core.size", new ParamValue.Scalar(99))
                .with("unknown.key", new ParamValue.Scalar(7));
        EffectParams safe = schema.coerce(edited);

        assertEquals(1.0, safe.scalarOr("core.size", -1), 1e-9, "clamped");
        assertEquals(7.0, safe.scalarOr("unknown.key", -1), 1e-9, "unknown keys are left alone");
    }

    @Test
    @DisplayName("the audit example reports a default the effect's own editor would refuse")
    void schemaAuditExample() {
        EffectSchema schema = EffectSchema.builder("Orb", "energy_orb", 1)
                .group("Core", ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"))
                .build();
        EffectParams shipped = EffectParams.builder().scalar("core.size", 5).build();

        assertTrue(SchemaAudit.audit(schema, shipped).stream()
                .anyMatch(p -> p.kind() == net.cyberpunk042.mcshaders.core.schema.SchemaProblem.Kind
                        .DEFAULT_OUT_OF_RANGE));
    }

    // ── docs: Editing a set of values ───────────────────────────────────────────

    @Test
    @DisplayName("the edit-session example coerces, tracks changes and undoes")
    void editSessionExample() {
        EffectSchema schema = EffectSchema.builder("Orb", "energy_orb", 1)
                .group("Core",
                        ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"),
                        ParamSpec.toggle("core.glow", "Glow", true, "Core"))
                .build();

        EditSession session = EditSession.of(schema);
        session.set("core.size", new ParamValue.Scalar(0.9));
        session.set("core.glow", new ParamValue.Flag(false));

        assertEquals(Set.of("core.size", "core.glow"), session.changedKeys());
        assertTrue(session.undo());
        assertTrue(session.current().flagOr("core.glow", false), "the toggle is back");

        // A set that changes nothing is not a step, as the prose claims.
        int before = session.historyDepth();
        assertTrue(!session.set("core.size", new ParamValue.Scalar(0.9)));
        assertEquals(before, session.historyDepth());
    }

    // ── docs: Keeping what was edited ──────────────────────────────────────────

    @Test
    @DisplayName("the tuning-store example survives the session that produced it")
    void tuningStoreExample() {
        EffectSchema schema = EffectSchema.builder("Orb", "energy_orb", 1)
                .group("Core", ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"))
                .build();

        TuningStore tuning = new TuningStore();

        EditSession session = tuning.sessionFor(schema);
        session.set("core.size", new ParamValue.Scalar(0.9));
        tuning.commit(session);

        assertEquals(0.9, tuning.effective(schema).scalar("core.size").orElseThrow(), 1e-9);

        // The claim the prose makes about a later sitting: it starts where this one
        // ended, not at the schema's 0.15 default.
        assertEquals(0.9,
                tuning.sessionFor(schema).current().scalar("core.size").orElseThrow(), 1e-9);

        // And the claim about an untouched type.
        EffectSchema untouched = EffectSchema.builder("Halo", "halo", 1)
                .group("Core", ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"))
                .build();
        assertTrue(tuning.get("halo").isEmpty());
        assertEquals(0.15, tuning.effective(untouched).scalar("core.size").orElseThrow(), 1e-9);
    }

    // ── docs: Turning a shape into geometry ────────────────────────────────────

    @Test
    @DisplayName("the geometry example tessellates, and detail is inert exactly as documented")
    void geometryExample() {
        Mesh mesh = Tessellator.tessellate(SphereShape.of(1.0f), 0);

        assertTrue(mesh.vertexCount() > 0, "the documented call produced no geometry");
        assertTrue(mesh.indices().length > 0, "the documented call produced no indices");
        for (int index : mesh.indices()) {
            assertTrue(index >= 0 && index < mesh.vertexCount(),
                    "an index addresses a vertex that does not exist: " + index);
        }

        // forEachTriangle is what the doc shows; check it visits every primitive
        // rather than silently doing nothing.
        int[] visited = {0};
        mesh.forEachTriangle((a, b, c) -> visited[0]++);
        assertEquals(mesh.primitiveCount(), visited[0],
                "forEachTriangle visited " + visited[0] + " of " + mesh.primitiveCount()
                        + " primitives");

        // "Resolution comes from the shape, not from the detail argument."
        CylinderShape coarse = CylinderShape.of(1.0f, 2.0f);
        CylinderShape fine = new CylinderShape(1.0f, 2.0f, 128,
                coarse.topRadius(), coarse.heightSegments(),
                coarse.capTop(), coarse.capBottom(), coarse.arc());

        assertEquals(Tessellator.tessellate(coarse, 0).vertexCount(),
                Tessellator.tessellate(coarse, 128).vertexCount(),
                "detail stopped being inert — the doc says it is");
        assertTrue(Tessellator.tessellate(fine, 0).vertexCount()
                        > Tessellator.tessellate(coarse, 0).vertexCount(),
                "raising the shape's own segment count did not produce a finer mesh");
    }
}
