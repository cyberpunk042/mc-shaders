# Using MC Shaders as a library

There are two ways to build on this, depending on whether you need Minecraft.

| You want to… | Depend on | Needs Minecraft |
|---|---|---|
| Add effects, backends or dimension looks to the mod | `net.cyberpunk042:mcshaders-api` | Yes |
| Use the effect-graph framework in any JVM project | `net.cyberpunk042:mcshaders-core` | No |

`mcshaders-api` depends on `mcshaders-core`, so the first coordinate gets you both.

## Adding the dependency

Artifacts are published to **GitHub Packages**, which requires authentication even
for public packages — a GitHub quirk, not a restriction of ours. Any token with
`read:packages` works.

```kotlin
repositories {
    maven("https://maven.pkg.github.com/cyberpunk042/mc-shaders") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("net.cyberpunk042:mcshaders-api:0.2.0")
    // or, off Minecraft entirely:
    // implementation("net.cyberpunk042:mcshaders-core:0.2.0")
}
```

Put `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`, never in the repo.

## What is stable

Types marked `@Stable` do not break within a major version. Types marked
`@Experimental` may change in any release — use them, but pin your version.
Anything unmarked is internal, even where it is `public` for technical reasons.

`McShadersAPI.API_VERSION` reports the contract version, which changes only when the
supported surface does — not on every mod release.

## Registration and its lifecycle

Registration is open during mod initialisation and **closes on first use** of the
rendering backend. Register from your own initialiser and you are always in time;
register after the first frame and you get an `IllegalStateException` rather than a
silent no-op.

Closing on first use rather than at a fixed lifecycle event is deliberate: Fabric
and NeoForge disagree about which event runs after every mod has initialised, and
first use is a point both reach only once everyone has had their turn.

Load order between mods is not guaranteed, so nothing depends on it:

- **Backends** are chosen by declared priority, not registration order.
- **Effect types** are refused on collision rather than last-write-wins, so one mod
  cannot silently shadow another's effect.
- **Bindings** merge by priority, and within a binding by layer id.

## Contributing a dimension look

The common case: give a dimension a visual identity.

```java
EffectStack look = EffectStack.of(
        EffectLayer.builder("haze")
                .kind(EffectKind.DISTORT)
                .params(EffectParams.builder().scalar("amplitude", 0.02).build())
                .build(),
        EffectLayer.builder("grade")
                .kind(EffectKind.COLOR_GRADE)
                .params(EffectParams.builder().color("tint", 0.8f, 0.9f, 1.0f, 1.0f).build())
                .build());

McShadersAPI.registerBinding(DimensionBinding.of(
        "mymod:dreamscape", DimensionId.parse("mymod:dreamscape"), look));
```

Conditions gate a binding on world state — and because merging is per layer id, a
conditional binding can adjust one layer of a dimension's look without restating it:

```java
McShadersAPI.registerBinding(new DimensionBinding(
        "mymod:dreamscape_night",
        DimensionId.parse("mymod:dreamscape"),
        new Condition.TimeOfDay(13000, 1000),   // wraps across midnight
        EffectStack.of(EffectLayer.builder("grade").kind(EffectKind.COLOR_GRADE)
                .params(EffectParams.builder().color("tint", 0.3f, 0.3f, 0.6f, 1.0f).build())
                .build()),
        10));                                    // higher priority wins on conflict
```

`haze` from the base binding survives untouched; only `grade` is overridden.

## Contributing a new effect type

An effect type is a declaration: an id, defaults, and what a backend must be able to
do. The id is namespaced so two mods can both ship a `swirl`.

```java
EffectDefinition kaleidoscope = EffectDefinition.of("mymod:kaleidoscope", "mymod")
        .withDefaults(EffectParams.builder()
                .scalar("segments", 6.0)
                .scalar("rotation", 0.0)
                .build());

McShadersAPI.registerEffect(kaleidoscope);

EffectLayer layer = EffectLayer.builder("swirl")
        .definition(kaleidoscope)
        .params(EffectParams.builder().scalar("segments", 12.0).build())
        .build();
```

Defaults fill gaps only — `segments` stays 12, `rotation` becomes 0.

**A definition does not render itself.** Declaring `mymod:kaleidoscope` says what the
effect *is*; some backend still has to know how to draw it. A backend advertises the
types it implements via `BackendCapabilities.withTypes(...)`, and layers naming a
type no available backend implements are skipped with a warning. So a custom effect
in practice ships alongside a backend that understands it.

## Contributing a backend

Backends are how the framework stays renderer-agnostic — the reason the whole design
puts an interface between itself and the graphics API.

```java
public final class MyBackendFactory implements BackendFactory {
    @Override public String id() { return "mymod:fancy"; }

    // Above DEFAULT_PRIORITY to take over from the built-in renderer;
    // below it to act as a fallback.
    @Override public int priority() { return BackendFactory.DEFAULT_PRIORITY + 100; }

    // Cheap and side-effect free. Real allocation belongs in initialise().
    @Override public boolean isAvailable() { return MyGraphics.isSupported(); }

    @Override public EffectBackend create() { return new MyBackend(); }
}

McShadersAPI.registerBackend(new MyBackendFactory());
```

Selection walks available factories from highest priority down and initialises each
until one succeeds, so a backend that probes fine but fails to allocate falls through
to the next candidate. A factory that throws is contained and reported, never fatal.
If nothing succeeds the result is a no-op backend — effects are disabled, the game is
not.

Your `EffectBackend` receives an `EffectGraph`: an ordered, already-validated list of
passes. Unsupported effects are gone, defaults are resolved, and the pass limit is
enforced, so you can walk it directly.

```java
@Override
public BackendCapabilities capabilities() {
    return new BackendCapabilities(
            "MyGraphics 1.0",
            Set.of(EffectKind.COLOR_GRADE, EffectKind.FOG),
            Set.of("mymod:kaleidoscope"),   // custom types you implement
            true,                            // depth buffer available
            8);                              // max passes per frame
}
```

Declare capabilities honestly. Claiming `FOG` without depth gets fog rejected anyway —
rendering it without depth produces a flat wash, and shipping that silently is worse
than skipping it.

## Using the core off Minecraft

`mcshaders-core` has no Minecraft dependency and no graphics dependency. It is a
general effect-graph library: describe effects as data, blend between sets of them
over time, compile against a target's capabilities.

```java
BindingRegistry bindings = BindingRegistry.of(
        DimensionBinding.of("scene", DimensionId.parse("app:scene"), look));

ShaderPipeline pipeline = new ShaderPipeline(myBackend, bindings);

// Per frame:
WorldState state = WorldState.of(DimensionId.parse("app:scene"))
        .withDayTime(18000)
        .withWeather(Weather.RAIN);

pipeline.frame(state, deltaTicks, new EffectBackend.FrameContext(width, height, 0f, elapsed));
```

`WorldState` is plain data with Minecraft-shaped fields; nothing stops you feeding it
from another source. `DimensionId` is just a validated namespaced id.

## Working with GLSL source

`core.glsl` resolves `#include` without a GPU or a resource pack. Give it a
`SourceProvider` — in the game the resource manager, in a test a map, in a
build-time tool the filesystem — and it hands back one flattened source with
`#line` directives, so a driver's error still points at the file a person has to
edit.

```java
SourceProvider files = path -> Optional.ofNullable(sources.get(path));

ResolvedShader resolved = new IncludeResolver(files).resolve("post/tint.fsh");

if (resolved.hasErrors()) {
    resolved.errors().forEach(System.err::println);   // path, line, and what is missing
} else {
    compile(resolved.source());
}
```

Including the same file twice is fine and happens once. A cycle is an error rather
than a truncation, because a shader that silently loses half its includes fails
somewhere far from the cause.

## Checking a uniform block against its shader

A `layout(std140) uniform` block is bound by byte offset, never by name, and is
declared at least twice — once in the shader, once by whatever writes it. Nothing
in a normal toolchain checks that those agree, and when they drift the shader
keeps reading numbers; they are simply the wrong numbers.

`core.layout` compares them and reports the offset where they part company:

```java
UniformBlock inShader = GlslBlocks.blocks(resolved.source()).get("Config");

UniformBlock fromHost = new UniformBlock("Config", List.of(
        new Std140.Member("Radius", GlslType.FLOAT),
        new Std140.Member("Tint", GlslType.VEC4)));

for (LayoutMismatch m : LayoutComparison.errors(inShader, fromHost)) {
    System.err.println(m);   // e.g. DIVERGENT_MEMBER at byte 4: ...
}
```

Two differences are deliberately not reported, because a checker that cries wolf
gets switched off: a matrix or array spelled as its elements — some content formats
have no matrix type, so a `mat4` must be four `vec4`s — and a host writing into a
slot the shader names `Reserved`. Use `compare` rather than `errors` to see those,
at `INFO`.

## Validating a post-processing chain

`core.chain` models a chain — targets, passes, inputs, outputs — and checks it
against the shaders it names. Core does no parsing: build a `PostChain` from
whatever format you use.

```java
ChainValidator validator = new ChainValidator(files, Set.of("minecraft:main"));

for (ChainProblem problem : validator.validate(chain)) {
    System.out.println(problem);
}
```

It catches a shader that moved, an include that no longer resolves, a target read
before anything wrote it, a target nothing reads, an input whose sampler the shader
does not declare, and any uniform block the two sides disagree about. What it
cannot tell you is whether the GLSL compiles — which is worth knowing, and is not
usually what is wrong.

## Describing what is tunable

`EffectParams` is the right shape for rendering and useless for editing: nothing in
a flat map says which of forty numbers belong together, what any is called, or what
a sane value looks like. `core.schema` says that, and holds no values itself, so
one description drives a screen in the game, a web page, or a command line.

```java
EffectSchema schema = EffectSchema.builder("Energy Orb", "energy_orb", 1)
        .group("Core",
                ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"),
                ParamSpec.toggle("core.glow", "Glow", true, "Core"))
        .group("Look",
                ParamSpec.color("look.tint", "Tint", ParamValue.Rgba.opaque(1, 1, 1), "Look"))
        .build();

EffectParams defaults = schema.defaults();
EffectParams safe = schema.coerce(edited);   // clamps, snaps, leaves unknown keys alone
```

A colour is one control, not four sliders. Bounds come from `Bounds.of(ValueRange)`
so a schema shares the vocabulary of ranges the rest of the library uses. A default
that its control could not hold is refused at construction rather than producing an
editor that cannot round-trip its own starting value.

`SchemaAudit.audit(schema, params)` compares a schema against the effect it claims
to describe — a control bound to a key nothing carries does nothing when dragged, a
parameter no control reaches cannot be edited, and a shipped default outside its own
declared range changes the first time someone opens the panel and closes it again.

## Compatibility notes

- **Optional integration**: guard your calls with your loader's "is this mod present"
  check rather than catching `NoClassDefFoundError`.
- **Errors are refusals, not warnings**: duplicate effect types and duplicate backend
  ids throw. That is deliberate — silently shadowing another mod's contribution
  surfaces later as an unexplained visual difference rather than an error.
- **Malformed input degrades**: unsupported effects, exceeded pass limits and
  unregistered types produce warnings on the `EffectGraph` and a skipped effect. A
  broken pack should cost some visuals, never a session.
