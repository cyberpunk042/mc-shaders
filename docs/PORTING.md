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

92 block declarations across 21 pipelines; 64 agree. The errors:

| Block | Where it breaks | What it means |
|---|---|---|
| `FieldVisualConfig` | diverges at byte 264, in all 25 field-visual passes | shader has `GeoWaveResolution`, host writes `GeoSmoothRadius`; the two lists never realign, so roughly two-thirds of the block is read from the wrong place |
| `MagicCircleConfig` | diverges at byte 56 | shader has `BreathTime`, host writes `UpZ` |
| `ShockwaveConfig` | truncated at byte 144 | shader reads `ShapeType` onward; the host stops before it |
| `VirusBlockParams` | truncated at byte 288 | `BlockPos` is `vec4[32]`; only element 0 is ever written |

### Which pair of declarations was compared

The table above compares the **pipeline JSON against the GLSL**. Two caveats
follow from that, and both matter before acting on it:

- The **Java record agrees with the GLSL** on the two things that were checked:
  both come to 928 bytes, and both put their two `mat4` members at slots 33 and
  37. Per-member names between record and GLSL were *not* compared — that needs
  the ~50 nested `@Vec4` types expanded — so "the record is correct" is not
  established here, only "the record is not obviously misaligned".
- `PostEffectPassMixin` builds and substitutes its own buffer rather than filling
  the one the pipeline JSON declares, so the JSON's byte total is not necessarily
  what the GPU sees. That does not make the divergence harmless — the JSON is
  still one of three declarations of one layout, and it disagrees with the other
  two — but it does mean the short-buffer reading is unproven.

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

21 pipelines. One — `shockwave_glow` — is sound. The rest:

| Pipelines | Error |
|---|---|
| `depth_test`, `depth_full`, `depth_passthrough`, `depth_redtint` | their fragment shader is gone: `post/depth_*.fsh` moved to `_archive/`, and the pipeline JSON was never updated |
| 16 `field_visual_*` | `FieldVisualConfig` diverges at byte 264 |
| `magic_circle` | `MagicCircleConfig` diverges at byte 56 |
| `shockwave_ring` | `ShockwaveConfig` truncated at byte 144 — 40 members unwritten |
| `virus_block` | `VirusBlockParams` truncated at byte 288 — 31 unwritten |

The four `depth_*` are worth separating from the rest. `DepthTestShader` is live
— reachable by keybind and from the GUI, with two mixins behind it — but
`loadPostEffect` throws on the missing shader, the throw is caught and logged,
and the call returns null. So all four modes are wired up and do nothing, quietly.
That is the same shape of failure as the layout drift, one level up: the
information needed to notice was there all along, and nothing was looking.

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
