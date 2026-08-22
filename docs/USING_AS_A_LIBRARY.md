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

## Editing a set of values

`EditSession` is the sitting between a schema and the values: it coerces each edit
to what the schema permits, remembers enough to undo it, and knows what has changed
since the sitting began.

```java
EditSession session = EditSession.of(schema);

session.set("core.size", new ParamValue.Scalar(0.9));   // coerced to the spec
session.set("core.glow", new ParamValue.Flag(false));

session.changedKeys();   // ["core.size", "core.glow"] — what to mark as touched
session.undo();
session.current();       // hand this to the renderer
```

The session performs the edit rather than offering a history to push to. The
alternative works until someone forgets to push, and then an edit is quietly
un-undoable — a bug that surfaces nowhere near the omission. Two consequences
worth knowing:

- **A change that changes nothing is not a step.** Dragging a slider away and back
  would otherwise fill the history with steps that do nothing when undone.
- **Coercion happens before that test**, so two different sets that clamp to the
  same value are one edit, not two.

## Asking what a dimension looks like

Contributing a binding is one half; reading back what the dimension actually became
is the other. Several mods and any number of datapacks can speak about the same
dimension, so the answer is rarely just what you registered.

```java
McShadersAPI.bindings();          // every binding in force, after registration closes
McShadersAPI.look(worldState);    // the stack that would be drawn here, merged and ordered
```

`look` is a read. It advances no transition and disturbs no frame, so it is safe
from a command, a HUD, or another mod's logic. Merging is by layer id in ascending
priority — the same stack the renderer resolves — so a high-priority binding that
redefines one layer leaves the rest intact.

### Replacing everything, as a reload does

```java
McShadersAPI.reloadBindings(registry);   // wholesale; reaches the running pipeline
```

This is deliberately allowed **after** registration closes, which is what separates
it from `registerBinding`. Registration is a startup accumulation, closed once so
mods cannot race each other. A reload is a runtime replacement — and refusing it
after close would mean `/reload` could never change anything.

It is wholesale rather than a merge: a reload's result is the complete new set, and
merging would leave bindings from a pack the player has just removed. Passing
`null` means empty, which is what a reload that found no binding files legitimately
produces.

### Starting from pack files rather than a registry

`reloadBindings` wants a `BindingRegistry`, and if what you have is a stack of JSON
files, this is where they become one:

```java
var result = McShadersAPI.loadBindings(files);   // Map<String, String>: name -> contents
result.problems().forEach(p -> LOGGER.warn("{}: {}", p.source(), p.message()));
```

The key of each entry is only ever used in error messages, so make it a path a pack
author would recognise.

One malformed file is skipped rather than taking every dimension's look down with
it, and a binding a later pack overrode is reported too. That leniency is only
defensible if the problems are seen — **a caller that discards the result has turned
a loud failure into a silent one.** Log `problems()`.

Applying is wholesale, for the same reason `reloadBindings` is. Passing no files
empties the registry, which is what removing the last pack should do.

## Keeping what was edited

A session is a sitting, not storage. When it goes out of scope so do its values,
which is fine for a one-shot edit and wrong for anything a person closes and comes
back to. `TuningStore` is where a finished sitting goes.

```java
TuningStore tuning = new TuningStore();       // or McShadersAPI.tuning()

EditSession session = tuning.sessionFor(schema);   // starts where the last one ended
session.set("core.size", new ParamValue.Scalar(0.9));
tuning.commit(session);                            // filed under the schema's own type

tuning.effective(schema);   // tuned if it has been, otherwise the schema's defaults
```

`effective` is what a renderer wants: it collapses "never edited" and "edited" into
one answer, so no caller has to remember that a missing entry means the defaults
rather than nothing. `get` keeps the two apart for callers that care.

Two deliberate choices:

- **`commit` takes the session, not a type and some values**, so values cannot be
  filed under a type they do not describe.
- **Committing values equal to the defaults still leaves an entry.** The store
  records what the editor last held, not whether it differs. The alternative —
  dropping the entry when nothing looks changed — silently discards earlier tuning
  the moment someone opens the editor and closes it without touching anything.
  `clear` is how an entry goes away, and it says so where it is called.

Unlike the registries beside it, a tuning store is never frozen: registries
accumulate during initialisation and then stop, whereas tuning is the thing that
changes while the game runs. It is concurrent, and the values in it are immutable,
so the render path can read it at any time.

## Turning a shape into geometry

Everything above describes effects. The other half of the library is geometry: the
field system's shapes are parameter records — a sphere is a radius, a segment count
and an algorithm — and `core.mesh` is what turns one into vertices.

```java
Mesh mesh = Tessellator.tessellate(SphereShape.of(1.0f), 0);

mesh.vertexCount();      // how many points
mesh.indices();          // triangle indices, all inside [0, vertexCount)
mesh.primitiveType();    // TRIANGLES, QUADS, LINES, ...

mesh.forEachTriangle((a, b, c) -> emit(a, b, c));
```

A `Mesh` is plain data: positions, normals, texture coordinates, indices. Handing it
to a graphics API is yours to do, and core deliberately does not reach for one — that
is the same line the rest of the library draws.

**Resolution comes from the shape, not from the `detail` argument.** No tessellator
reads `detail`, and the `DetailLevel` enum beside it is equally inert; both predate
the move to shape-carried resolution and were left behind. To change how fine a mesh
is, change the shape's own `segments`, `heightSegments` or `subdivisions`:

```java
CylinderShape coarse = CylinderShape.of(1.0f, 2.0f);          // 32 segments
CylinderShape fine   = new CylinderShape(1.0f, 2.0f, 128,     // 128 segments
        coarse.topRadius(), coarse.heightSegments(),
        coarse.capTop(), coarse.capBottom(), coarse.arc());
```

They are documented rather than deleted because removing them is a breaking change
to make deliberately. `TessellationTest` pins the behaviour, so if a tessellator ever
starts honouring `detail`, a test fails and this paragraph gets corrected with it.

`Tessellator.tessellate` dispatches on shape type and covers sphere, ring, prism,
cylinder, polyhedron and molecule. A shape it does not recognise yields
`Mesh.empty()` and a warning rather than an exception — a consumer passing an
unfamiliar shape should get nothing drawn, not a thrown frame. The rest of the
tessellators — rays, jets, capsules, cones, tori — are called directly.

### Grouping primitives into a layer

A `Primitive` is one shape with its own configuration. A `FieldLayer` is what makes
several of them one thing — move the layer and they all move, fade it and they all fade,
and links resolve within it, so a layer is the scope in which "take your radius from
that one" means anything.

```java
FieldLayer layer = FieldLayer.builder("aura")
        .primitives(core, innerRing, outerRing)   // declaration order matters
        .transform(Transform.IDENTITY)
        .alpha(0.8f)
        .blendMode(BlendMode.ADD)
        .build();

layer.isDrawable();          // visible, not transparent, not empty
layer.primitive("core");     // Optional<Primitive>
```

Order is not cosmetic: links may only point backwards, so a layer that reordered its
contents would turn valid content into forward references. The list is copied on the way
in and unmodifiable on the way out, for the same reason.

`BlendMode` here is the same enum the effect layers use. It is one set of compositing
operations, and two enums whose constants mean the same arithmetic is the kind of
duplicate declaration that drifts apart. Content written against the field system's older
spelling maps `NORMAL` onto `ALPHA`.

**A layer holds; it does not render, and it does not resolve.** Turning one into geometry
is the sequence above — build the index, resolve, apply, tessellate — and there is
deliberately no single call that does it, because the apply step for a radius means
choosing what "set the radius" means per shape type. That is a decision about how content
behaves, not a gap in the plumbing.

### Links resolve to values; applying them is yours

A `PrimitiveLink` is a constraint, not a drawn wire: *take your radius from that one,
mirror its position, inherit its colour, orbit in step with it*. `LinkResolver` turns
those into numbers.

```java
Map<String, Primitive> index = LinkResolver.buildIndex(primitives);
LinkResolver.ResolvedValues resolved = LinkResolver.resolveLinks(ring, index);
```

Everything it returns is *computed*, never applied — primitives and shapes are
immutable records. For most of it that is the natural shape: `offset` and `scale` go
into a transform, `color` and `alpha` into a draw call, `orbitPhaseOffset` into an
animation clock.

**`radius` is the exception, and it is the one that bites.** Applying it means building
a different `Shape`, and "set the radius" is not one operation across shape types — a
sphere has one radius, a ring has an inner and an outer, a cylinder has a radius and a
top radius. Core does not choose for you, so `resolveRadius` hands back a number and
stops:

```java
Shape shape = ring.shape();
if (resolved.hasRadius() && shape instanceof SphereShape) {
    shape = SphereShape.ofRadius(resolved.radius());   // the step that is easy to omit
}
Mesh mesh = Tessellator.tessellate(shape, 0);
```

This is worth spelling out because in the mod this code came from, **nothing performed
that step**. The renderer consumed `offset`, `scale`, `color`, `alpha`, `orbitConfig`,
`orbitPhaseOffset` and `followDynamic` — and never `radius`. A content author writing
`radiusMatch` got a link that validated, resolved to the right number, and changed
nothing on screen. `LinkResolverTest` pins both halves: that resolving alone leaves the
shape alone, and that doing the last step is what makes the mesh change size.

### Diagnostics

The tessellators log what they were asked for and what they produced, through
`java.lang.System.Logger`. There is nothing to add and nothing to initialise: under
Minecraft it lands in the usual log, in a test it goes to the console, and in a
consumer that never configures logging it goes nowhere. Enable `DEBUG` on
`net.cyberpunk042.mcshaders.core.render` to see lines like

```
tessellate: Tessellating cylinder shape=cylinder radius=1.0 segments=3 wave=true
```

which is normally enough to explain a mesh that came out wrong. Nothing is formatted
while the level is off.

## Compatibility notes

- **Optional integration**: guard your calls with your loader's "is this mod present"
  check rather than catching `NoClassDefFoundError`.
- **Errors are refusals, not warnings**: duplicate effect types and duplicate backend
  ids throw. That is deliberate — silently shadowing another mod's contribution
  surfaces later as an unexplained visual difference rather than an error.
- **Malformed input degrades**: unsupported effects, exceeded pass limits and
  unregistered types produce warnings on the `EffectGraph` and a skipped effect. A
  broken pack should cost some visuals, never a session.
