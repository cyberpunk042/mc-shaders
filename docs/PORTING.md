# Porting the visual engine out of the-virus-block-mc

`the-virus-block-mc` contains a large visual engine: a shader corpus, a field system
of shapes and links, and a live customiser UI. The plan is to extract the reusable
engine into mc-shaders, leaving that mod as a consumer.

This file records the decisions that shape the extraction, so nobody has to
re-derive them — least of all the licensing one.

## Licensing: MIT engine, CC content

The two repositories are under different licences, and that is deliberate rather
than an accident to be tidied away.

| | Licence | What it holds |
|---|---|---|
| mc-shaders | MIT | the engine: field model, shapes, links, effect graph, backends, GUI framework |
| the-virus-block-mc | CC BY-ND-NC 4.0 | the content: the GLSL corpus, effect presets, field profile configs |

**Engine code moves and is relicensed MIT. Content stays where it is and is loaded
at runtime.** The author holds copyright on both, so the relicensing is theirs to
grant; this file records that it was a decision, not a side effect.

The split is not a compromise — it is what each licence is for. MIT on the engine is
what makes it a library other people can build on, which is the entire point of the
public API. CC BY-ND-NC on the artwork keeps the ND and NC protections the author
chose. And `LICENSE-CLARIFICATION.md` in the-virus-block-mc already permits exactly
the arrangement this needs: *"Runtime interaction, linking, or dependency by other
mods"*.

Practical consequence: **the engine must be able to load content it does not
contain.** Config parsing and resource lookup are engine responsibilities; the
configs themselves are not engine files. Anything that would require embedding a
CC-licensed asset into an MIT artifact is a design error, not a packaging detail.

### Provenance

Ported files carry a header naming their origin. Attribution is a term of the
licence they came from, and keeping it visible is cheaper than reconstructing it
later.

## What can move, measured

Minecraft coupling was measured per file rather than assumed:

| Area | Files | With `net.minecraft` imports | Without |
|---|---|---|---|
| `field/` | 112 | 48 | 64 |
| `client/visual/` | 161 | 39 | 122 |
| `client/gui/` | 145 | 70 | 75 |
| **Total** | **418** | **157** | **261** |

**261 of 418 files — around 46,500 lines — have no Minecraft import at all.** That is
the part that can move essentially as-is: version-independent, loader-independent,
and testable with no game running.

The remaining ~157 files plus 59 mixins are where the porting risk concentrates,
because they cross the 26.1 deobfuscation break and the OpenGL-to-Vulkan renderer
transition at the same time.

Caveat worth remembering: *no Minecraft import* is a first-order signal, not proof.
A file can depend transitively on one that does, and same-package references need no
import at all — which is exactly how the first port attempt missed two enums and
failed to compile.

## Dependency policy for `core`

`core` may depend on **pure-Java libraries from Maven Central**. It may never depend
on Minecraft or on a graphics API.

The property being protected is not "zero dependencies" for its own sake — it is
that the core builds and tests anywhere, with no game, no GPU, and no access to the
Minecraft Maven hosts. A maths library does not compromise that.

- **JOML** is allowed, and expected. The shape model uses `Vector3f`, and Minecraft
  itself uses JOML, so sharing it makes interop free rather than requiring a
  conversion layer at every boundary.

  **Its version tracks Minecraft, not latest.** Minecraft 26.2 declares
  `org.joml:joml:{strictly 1.10.8}` through `minecraft-dependencies`. A strict
  constraint admits no other version, so publishing a newer JOML from `core` makes
  the loader modules fail to resolve outright. Bump this only when Minecraft does.

  This one was found by CI, not by reading: `core` and `common` both build happily
  against any JOML, because neither sees Minecraft's constraint. Only the loader
  modules do.
- **Gson stays out of `core`.** JSON binding belongs in a module above it. Keeping
  the model free of serialisation concerns is what lets the same model be loaded
  from a datapack, a config file, or constructed in code — and under the licence
  split, loading external content is a first-class requirement.

## Order of extraction

Each step is independently useful and independently verifiable.

1. **GLSL include resolution** ✅ — the expansion the shader corpus depends on.
2. **Shape maths foundation** ✅ — `ShapeMath`, `SimplexNoise`, shape state and the
   shape vocabulary enums. Pure maths, now under test for the first time.
3. **Shape model** ✅ — `Shape` plus sphere, ring, cylinder and prism, with JSON
   binding split out and the `@JsonField` metadata retained for a codec layer.
4. **Motion model** ✅ — `Transform`, `OrbitConfig`/`OrbitConfig3D`, and the
   `animation` and `energy` packages `LinkResolver` needs.
5. **Field model: links and primitives** — the wires: follow, mirror, phase offset,
   radius match, orbit sync. `LinkResolver` is the substance.
6. **Effect and visual config model** — the contract between GUI, engine and GLSL.
7. **Rendering backend** — the first backend that actually draws.
8. **GUI framework** — the customiser, loader-neutral.
9. **the-virus-block-mc consumes the library.**

## Note on tests

The ported maths arrived with no tests. Characterisation tests were written against
it as it moved, which immediately paid for itself: the first attempt asserted that a
spheroid with equal `radius` and `length` is a sphere. It is not — `length` is a
*ratio*, so the sphere case is `length == 1`, and the deformation is
volume-preserving (`a²c = r³`). The tests now pin that invariant down, which is more
useful than what was originally asserted.

Porting untested code without writing tests as it moves means carrying its unknowns
across a version boundary and finding out later which of them mattered.

## Mechanics of stripping the JSON binding

The shape classes each carried a two-line `toJson()` delegating to a Gson-backed
serialiser. Removing it is mechanical, and was scripted — but three things about the
script are worth knowing before running it again on the remaining classes:

- **Rewrite imports *after* deciding what to drop, not before.** Renaming
  `net.cyberpunk042.util.json.JsonSerializer` to its new package first meant the
  drop-rule no longer recognised it, and the dead import survived.
- **An interface declares `toJson()` with no body.** Brace-matching finds nothing to
  remove, so the abstract declaration needs handling separately.
- **Removing a method orphans its `@Override`.** The orphan then binds to whatever
  method follows — and if that one is `static`, the compiler rejects it in a way that
  points at the wrong line entirely.

None of these are subtle once seen; all three cost a compile cycle.

## The same-package trap, twice

Both port attempts so far failed to compile for the same reason: a file that imports
nothing still references its own package. `ShapeMath` needed `CloudStyle` and
`EdgeTransitionMode`; the shape model needed `SphereDeformation`, `SphereAlgorithm`
and `OrientationAxis`, plus two `visual.effect` records reached by fully-qualified
name rather than import.

When porting the next batch, resolve the transitive closure first rather than
trusting the import list.

Doing exactly that for `Transform` paid off twice over. It bounded the batch
honestly at 38 files rather than the whole of `visual/` — and it surfaced two files
that are Minecraft-coupled despite sitting in a package that is otherwise clean:

- **`AnchorResolver`** takes a `PlayerEntity`. It does not belong in `core` at all;
  it belongs in `common`, and was dropped from this batch rather than mangled to fit.
- **`Waveform`** used Minecraft's `MathHelper.sin`, a lookup-table approximation.
  It is reimplemented on `java.lang.Math`, which is exact and marginally slower. For
  a waveform driving visuals that difference is imperceptible, but it *is* a
  behavioural change rather than a move, so it carries a comment saying so and the
  tests pin the landmarks down.

A third class of leftover only appears once the public JSON methods are gone:
**private helpers that took a `JsonObject` or `JsonArray`** and were only ever
called by them. They compile fine until their parameter type disappears.

## Uniform-block layout: three declarations, nothing checking them

The shader corpus in `the-virus-block-mc` surfaced a class of bug the engine is
now able to catch, and it is worth writing down because it is not specific to
that mod.

A `layout(std140) uniform` block is bound by **byte offset, never by name**. It
therefore has to be declared at least twice — once in the shader that reads it,
once by whatever writes it — and nothing in the toolchain checks that the two
agree. Insert one member into one side and every member after it shifts. The
shader keeps reading floats; they are simply the wrong floats. No error is
raised anywhere, at build time or at run time. The picture is just wrong.

`the-virus-block-mc` has *three* declarations of `FieldVisualConfig`:

| Declaration | Where | Role |
|---|---|---|
| GLSL block | `shaders/post/include/core/field_visual_base.glsl` | what the shader reads |
| Java record | `client/visual/effect/FieldVisualUBO.java` | what actually gets written |
| Pipeline JSON | `post_effect/field_visual_*.json` | what the pipeline declares |

Its own comment says the quiet part out loud — *"CRITICAL: This layout MUST
match across ALL effect shaders … any mismatch causes silent failures (NaN
values, wrong params in wrong uniforms)"* — and the mod contains a
`GLSLValidator` written to catch exactly this. It carries
`// TODO: Implement GLSL parsing when ready`, it has no callers, and the
`glslPath` it would look in names a file that does not exist.

`core.layout` is that check, finished and testable without a GPU.

### What it has to ignore to be worth running

A validator that reports every spelling difference gets switched off, and then
the real drift ships. Two differences are not defects:

- **A matrix or array spelled as its elements.** Minecraft's post-effect JSON
  has no matrix type, so a `mat4` must be written as four `vec4` rows. Both
  sides are expanded to elements before comparing, and an element's generated
  name is not treated as evidence. Before this rule the comparison flagged every
  `CameraDataUBO` in the corpus; after it, all 25 agree — correctly, the bytes
  being identical.
- **A slot the shader reserves.** If the shader calls a member `Reserved3_0` and
  the host writes `CameraX` there, the offsets line up and the shader ignores the
  value. Reported, at INFO.

Expanding arrays matters for more than noise. One `vec4 x[32]` is a single
declaration but thirty-two slots of data, so a host writing one of them has left
thirty-one unwritten. Counting declarations calls that a size difference;
counting slots calls it what it is.

### Findings against the corpus

92 block declarations across 21 pipelines; 64 agree. These are disagreements
between the pipeline JSON and the GLSL — see *Which pair of declarations was
compared*, below, for what that does and does not cause at runtime. The errors:

| Block | Where it breaks | What it means |
|---|---|---|
| `FieldVisualConfig` | diverges at byte 264, in all 25 field-visual passes | shader has `GeoWaveResolution`, host writes `GeoSmoothRadius`; the two lists never realign, so roughly two-thirds of the block is read from the wrong place |
| `MagicCircleConfig` | diverges at byte 56 | shader has `BreathTime`, host writes `UpZ` |
| `ShockwaveConfig` | truncated at byte 144 | shader reads `ShapeType` onward; the host stops before it |
| ~~`VirusBlockParams`~~ | — | **retracted.** Reported as truncated at byte 288 on the strength of a scratch script that ignored the JSON's `count` field. The pipeline declares `BlockPos` with `count: 32`, matching the GLSL's `vec4 BlockPos[32]` exactly. `virus_block` is sound. |

### Which pair of declarations was compared

The table above compares the **pipeline JSON against the GLSL**. Two caveats
follow from that, and both matter before acting on it:

- The **Java record agrees with the GLSL** as far as anything static can tell.
  Expanding its ~50 nested `@Vec4` types gives 208 entries against the GLSL's
  208, the same 928 bytes, and both matrices at slots 33 and 37. Their *names*
  turn out not to be comparable at all: the nested types use local component
  names — `PrimaryColorVec4` is `(r, g, b, a)`, not `(PrimaryR, PrimaryG, …)` —
  so from slot 1 onward there is nothing to match against. Structure agrees;
  whether slot *n* means the same thing on both sides cannot be established
  without reading each type's use, or running it.
- **The JSON's layout is overwritten before the pass draws, so the drift is
  latent rather than active.** `PostEffectPassMixin` injects at `render` HEAD and,
  for each of `FieldVisualConfig`, `MagicCircleConfig`, `ShockwaveConfig`,
  `VirusBlockParams` and the base UBOs, builds a buffer sized from the *Java
  record* and `put`s it into `uniformBuffers`, replacing whatever the pipeline
  declared. The JSON's role for these blocks is to create the map key at all —
  each updater begins `if (!uniformBuffers.containsKey(...)) return;` — not to
  determine the layout.

  So at runtime the bytes come from the record, which agrees structurally with the
  GLSL. The divergence is a genuine defect, and a trap for anyone who later
  removes or bypasses the mixin, but it is not what makes a frame look wrong.

  The one path where it does reach the GPU is narrow and worth naming: if the
  pass's field lookup returns null the updater returns *before* substituting, and
  that draw uses the pipeline-declared buffer — the short, divergent one.

Settling it needs either the record's members expanded and compared, or the
thing no static check can do: running it.


## The chain around the layout check

`core.layout` answers one question about one block. `core.chain` is the thing
that asks it, along with everything else that can be settled from text.

A `PostChain` is targets plus passes in order — the concrete, authored form of a
post-processing effect, below `EffectGraph`'s backend-neutral description. Core
models and checks it without owning it, which is the whole engine/content split
working as intended: the checker is MIT, the shaders it reads stay under their
own terms, and no content is copied to make the check possible.

`ChainValidator` reports:

| Check | Why it needs the chain, not just a pass |
|---|---|
| shader resolves | — |
| includes resolve | a missing include is reported as such, not as a missing shader; and nothing further is read out of a shader whose includes failed, because the flattened source is a guess |
| input target is declared or host-supplied | — |
| output target is declared or host-supplied | — |
| target read before written | invisible from inside either pass |
| target declared and never read | ditto |
| bound inputs match declared samplers | the `In` → `InSampler` suffix is convention neither side states; it lives in `Input.declaredSampler()` rather than being open-coded at each comparison |
| uniform block layouts agree | delegates to `core.layout` |

What is left for a GPU is whether the GLSL compiles. That is worth knowing and
is not usually what is wrong.

### The corpus, end to end

21 pipelines. Two — `shockwave_glow` and `virus_block` — are sound. The rest:

| Pipelines | Error |
|---|---|
| `depth_test`, `depth_full`, `depth_passthrough`, `depth_redtint` | their fragment shader is gone: `post/depth_*.fsh` moved to `_archive/`, and the pipeline JSON was never updated |
| 16 `field_visual_*` | `FieldVisualConfig` diverges at byte 264 |
| `magic_circle` | `MagicCircleConfig` diverges at byte 56 |
| `shockwave_ring` | `ShockwaveConfig` truncated at byte 144 — 40 members unwritten |
| ~~`virus_block`~~ | retracted — sound. See the note in the findings table above. |

The four `depth_*` are worth separating, and then separating again once you read
what they are. `DepthTestShader` is reachable — keybind, a GUI node, two mixins —
and `loadPostEffect` throws on the missing shader, the throw is caught and logged,
and the call returns null, so all four modes are wired up and quietly do nothing.

But its own javadoc describes a depth-buffer *testing* harness driven by a
`/depthtest` command, and `WorldRendererDepthTestMixin` calls it legacy and
prefers `DirectDepthRendererArchive` and `ShockwaveGlowRendererArchive` ahead of
it. The four `.fsh` files are not lost: they sit in `post/_archive/`. Read
together, that is a debug harness that was deliberately retired, leaving the
wiring behind — not shaders that went missing by accident.

Which makes the fix a judgement call rather than a repair. Restoring the files
would revive superseded tooling; deleting the four pipelines and reducing the
mode list to what still works would retire it properly. Both are reasonable and
neither is the checker's to decide. What the checker is for is that nobody had to
guess: the pipelines say what they need, the tree says what is there, and the two
disagree.

Vanilla shader ids (`minecraft:post/blit`, `minecraft:post/sobel`) resolve
through the resource manager in game. A checker pointed at a mod's asset tree
alone will not find them, which is a property of the provider it is given, not of
the chain.


## Running the check without the game

`core` models chains and checks them; it does not parse. `check/` is the module
that closes the loop — a JSON codec, a resource-tree `SourceProvider`, and a CLI:

```sh
cd check && ../gradlew run --args="/path/to/assets"
```

It is a separate build with no Minecraft dependency for the same reason `core`
is, plus one specific to it: a shader pack should be checkable in someone else's
CI, by someone with no interest in building this mod.

Two rules in the codec are load-bearing and neither is obvious from a sample of
the format:

- **A uniform entry's position is its layout.** The list is not a bag of named
  values; it is the std140 declaration of the block, and each entry's index
  decides which bytes the shader reads it from. So the codec preserves order
  exactly, and refuses an unknown type rather than skipping the entry — skipping
  one would shift every later member and produce a confident, wrong comparison.
- **`value` is ignored.** Defaults do not affect layout, and layout is the
  question.

The checker also has to distinguish *missing* from *elsewhere*. A chain naming
`minecraft:post/blit` is not broken because the game's own shaders are not in the
mod's asset tree; `ResourceTree.isExternal` reports that a namespace is absent
entirely, and findings about those ids are dropped. Without it every chain in the
corpus reports two missing shaders, which is exactly how a checker gets ignored.


## A note on the scratch scripts

The findings in this document were first produced by throwaway Python, then
re-derived through `check/` once it existed. That was worth doing: one finding
did not survive.

`virus_block` was reported as writing one element of a thirty-two element array.
The scratch script ignored the JSON's `count` field; the pipeline declares
`BlockPos` with `count: 32` and matches the shader exactly. The library was
right and the script was wrong — which is the argument for the library, and the
reason a finding from a scratch tool is a hypothesis until the real one agrees.


## The editing schema

The mod carries a 40,000-line GUI. Most of it is Minecraft `Screen` plumbing and
stays where it is, but measuring the coupling the same way as before found a
clean layer inside it: `schema`, `state`, `annotation` and their neighbours —
around 54 files with no `net.minecraft` import.

The most self-contained part of that is the descriptor layer, and it is what
`core.schema` now is. An effect's parameters are a flat map at runtime, which is
right for the render path and useless for editing: nothing in it says which of
forty numbers belong together, what any of them is called, or what a sane value
looks like. A schema supplies exactly that and holds no values itself.

It is not a copy. Three things changed on the way across, each because the
original shape caused a problem worth not repeating:

| Upstream | Here | Why |
|---|---|---|
| every value is a `float`; a toggle is `0f`/`1f` | the default is a `ParamValue` | a colour is one control, not four unrelated sliders — which is a large part of why `FieldVisualConfig` has two hundred entries |
| `min`/`max`/`step` restated per parameter | `Bounds.of(ValueRange)` | the project already has a vocabulary of named ranges; restating `0f, 1f` by hand drifts from it silently |
| nothing checks the default against the control | the constructor refuses it | a `COLOR` defaulting to a scalar gives an editor that looks correct and cannot round-trip its own starting value |

Two behaviours are worth stating because they are easy to get backwards:

- **An override keeps the original position.** A later version narrowing or
  relabelling an inherited parameter should not make the panel jump around, so
  the last declaration wins on content and the first wins on placement.
- **Coercion leaves unknown keys alone.** An editor is not the only thing that
  writes parameters. Dropping what a schema does not recognise would make opening
  a panel destructive.

The schema registry — which effects exist and what each exposes — is content, not
engine, and stays in the mod. `SchemaContentBuilder`, which turns specs into
Minecraft widgets, is plumbing and stays too. What crosses is the description,
which is the part any front end needs and none of them should own.

### Auditing a schema against the effect it describes

A schema and an effect's parameters are two declarations of one thing — what an
effect is tunable by — and nothing keeps them in step. That is the third time
this shape has come up here, after the include graph and the uniform blocks, and
the failures are quiet in the same way:

| Finding | What it means |
|---|---|
| `UNBACKED` | a control is offered for a key the effect does not carry, so dragging it does nothing |
| `UNREACHABLE` | the effect carries a parameter no control reaches, so it cannot be edited |
| `SHAPE_MISMATCH` | the effect holds a colour where the schema offers a slider, or similar |
| `DEFAULT_OUT_OF_RANGE` | the effect ships a value its own editor would refuse |

The last is the sharpest, and the reason `SchemaAudit` exists rather than the
schema simply being trusted. A default outside its own declared range is changed
the first time someone opens the panel and closes it again — so a fresh install
and an edited one render differently, from a control nobody touched. Nothing
about that is visible while it happens.

`UNREACHABLE` is informational rather than a failure: an effect may legitimately
carry parameters that are not meant to be edited by hand.


## The two projects target different Minecraft versions

Worth stating plainly, because it changes what "port this across" can mean:

| | `the-virus-block-mc` | `mc-shaders` |
|---|---|---|
| Minecraft | **1.21.6** | **26.2** |
| Mappings | `yarn_mappings=1.21.6+build.1` | none — 26.x ships unobfuscated |
| Loom plugin id | `fabric-loom` (legacy) | `net.fabricmc.fabric-loom` |

Every `net.minecraft` name in the mod is a **Yarn** name, and Yarn stopped being
maintained from 26.1 onward because Mojang's own names are now in the jar. So the
mod's Minecraft-facing code cannot cross by recompiling.

**But not uniformly, and the exceptions are the dangerous part.** Read against a
Fabric mod shipping on 26.2 ([`Snownee/Jade`](https://github.com/Snownee/Jade/tree/26.2-fabric)):

| Yarn, in the mod | 26.2, in a mod that builds | |
|---|---|---|
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` | renamed |
| `net.minecraft.client.font.TextRenderer` | `net.minecraft.client.gui.Font` | renamed and moved |
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` | renamed and moved |
| `net.minecraft.util.Identifier` | `net.minecraft.resources.Identifier` | **same class name, different package** |
| `net.minecraft.item.ItemStack` | `net.minecraft.world.item.ItemStack` | same name, different package |

An earlier draft of this section said the identifiers "differ", full stop. That is
wrong in the way that costs the most time: `Identifier` and `ItemStack` keep their
names and move package, so a blind find-and-replace on class names leaves imports
that look right, resolve to nothing, and give an error naming a class that plainly
exists. Assume every name changed *or* every name survived and you are wrong
either way — each one has to be looked up.

That is roughly 40,000 lines of GUI and several thousand of render code which are
not portable as written, on top of being coupled to Minecraft at all.

**It also retroactively justifies the rule this port has followed throughout.**
Every batch excluded anything with a `net.minecraft` import — originally on the
grounds that core must stay dependency-free. The stronger reason only became
visible on checking the versions: that code is written against names which do not
exist in the version this project targets. A port that had taken the "easy" route
of moving Minecraft-facing classes and fixing them up would have been rewriting
every one of them, not adjusting a few.

What crossed instead — geometry, maths, the field model, the parameter model — is
version-agnostic pure Java, and is unaffected by any of this.

**The shader corpus is 1.21.6-era too, and that turns out not to matter.** The
caveat originally recorded here — that the post-effect format might have changed
by 26.x — was checked rather than left open. Vanilla's own `post_effect/blur.json`
and `entity_outline.json` at **26.1.2** use exactly the same shape:

| | 1.21.6 corpus | vanilla 26.1.2 |
|---|---|---|
| top level | `targets`, `passes` | same |
| target entry | `{}` | same |
| pass | `vertex_shader`, `fragment_shader`, `inputs`, `output`, `uniforms` | same |
| input | `sampler_name`, `target`, `use_depth_buffer` | plus `bilinear` |
| uniform entry | `name`, `type`, `value` | same |

So the format did not change, the corpus is portable in that respect, and the
checker's model is current for 26.x.

The comparison also found the one field the model was missing. Vanilla's blur
passes set `bilinear` on an input to sample a half-resolution target smoothly;
the mod's corpus never uses it, so reading only that corpus would never have
surfaced it, and a chain read and written back would have silently lost the
field. `Input` now carries it.

The std140 rules the checker enforces are a GLSL and OpenGL matter and never
depended on the Minecraft version.

## Compiling the GLSL, which turned out not to need a GPU

Every earlier note here said the one thing left for a driver was whether the GLSL
compiles. That was wrong in a useful way: it needs a *compiler*, not a driver.
Khronos' `glslangValidator` parses and type-checks a shader with no context and
no hardware.

Run over the corpus, **28 of 30 shader files compile clean**. The two that do not
are `core/fresnel_entity.fsh` and `.vsh`, which use `#moj_import` — Minecraft's
own include directive, which the game's loader expands and glslang rejects as
unknown. That is a gap in the checker, not a defect in the shaders, so those are
skipped rather than reported. Both are also files no chain reaches.

The step is optional in the tool and installed in CI. The module stays pure Java
with no native dependency, because demanding an external binary to run *any* of
it would make it harder to adopt than the problems it finds are worth; CI
installs `glslang-tools` so the stronger check is the one that actually runs
there.

**What a pass means.** glslang is the reference front end, not the driver that
will run the shader. A pass means the source is valid GLSL — the overwhelming
majority of what goes wrong — not that a given driver accepts it, and certainly
not that it draws what was intended. Those remain a GPU's business.

One reporting detail worth keeping: when everything compiles, the checker *says
so*, naming the validator's version. Silence would be indistinguishable from the
step not having run, which is the failure mode of every optional check.
