# The shader part, explained

"Shaders in Minecraft" means three unrelated things. Most confusion about what this
mod does — and most disappointment — comes from expecting one of them and getting
another. So this page separates them, says which one MC Shaders++ touches, and is blunt
about how far that has actually got.

## Three different things people call shaders

### 1. Shader packs — the Iris/OptiFine kind

Replace how the world is drawn from the ground up: shadows, volumetric light, water
that reflects. They swap out Minecraft's whole rendering path.

**MC Shaders++ is not one of these and is not trying to be.** If you install it expecting
BSL or Complementary, you will be disappointed. It aims to *cooperate* with them
eventually — see the roadmap's M6 — not to compete.

### 2. Post-processing chains — the vanilla kind

Minecraft finishes drawing a frame, then can run extra passes over the finished image,
the way a photo filter works. That is how the creeper-view green tint and the spectator
effects are done.

A chain is a JSON file under `post_effect/` naming GLSL programs and the render targets
they read and write. Vanilla ships several; a pack can add its own.

**MC Shaders++ does not run its own chains yet.** The entry points for hooking one into
26.2 have not been read out of any source that compiles on 26.2, and this project does
not guess at APIs — see [RENDERING-26.2.md](RENDERING-26.2.md).

It *can* check them, though, which is a separate and useful thing — see [The checker](#the-checker).

### 3. Atmosphere — fog, and it is not a shader at all

Every frame, the game computes a handful of numbers: where fog starts, where it ends,
what colour it is. Those numbers are handed to the renderer. Changing them changes how
the world looks without writing a single line of GLSL.

**This is what MC Shaders++ does today.** All of it.

## So what does it actually do

Give a dimension a look, as data:

```json
{
  "id": "overworld_storm",
  "dimension": "minecraft:overworld",
  "priority": 20,
  "condition": { "type": "weather", "weather": "thunder" },
  "stack": {
    "layers": [{
      "id": "atmosphere",
      "kind": "fog",
      "type": "mcshaders:fog",
      "params": {
        "start": 8.0,
        "end": 56.0,
        "color": { "r": 0.325, "g": 0.361, "b": 0.404, "a": 1.0 }
      }
    }]
  }
}
```

Drop that in a datapack at `data/<yournamespace>/mcshaders/bindings/`, run `/reload`,
and the Overworld's fog closes in when it thunders. No Java, no GLSL.

Three ideas carry the whole design:

**Looks are data, not code.** A look is a stack of layers, each naming an effect type
and its parameters. Nothing in it says *how* to draw — which is what lets the same look
render on whatever backend is available.

**Conditions are declarative.** `y_range`, `weather`, `biome_tag`, `submerged`, combined
with `all` / `any` / `not`. One caveat, stated because it will otherwise waste your
afternoon: **`time_of_day` cannot work on 26.2.** The day time is not readable from the
client any more, so a binding gated on it silently never fires. The mod logs which of
your bindings this affects at startup.

**Layers merge by id, across bindings.** Two bindings on one dimension writing a layer
called `atmosphere` do not stack — the higher-priority one *replaces* that layer and
leaves the others alone. So a storm look can override the base atmosphere without
restating everything else.

## What happens each frame

```
binding files ──> registry ──> pipeline ──> backend ──> the frame's fog
   (datapack)      (merged      (resolves    (publishes   (mixin on Fabric,
                    by id)       + eases)     values)      event on NeoForge)
```

The pipeline eases between looks rather than snapping, so crossing a biome edge during
a storm fades rather than cuts.

## Can I bring my own shaders?

The honest answer is **not yet, and it is worth being precise about what that means**,
because part of it works today and the part that does not is the interesting one.

| You want to | Today |
|---|---|
| Author a **look** — fog, per dimension, on conditions | **Yes.** Datapack JSON, no code |
| Declare a new **effect type** of your own | **Yes**, but a declaration draws nothing on its own |
| Write a **backend** that draws your effect | **Yes.** You write the graphics code; the framework routes to it |
| Hand the framework **your GLSL** and have it run it | **No.** Nothing here loads shader source at runtime |
| Check a vanilla-style chain you already have | **Yes**, and it needs none of the above — see [The checker](#the-checker) |

### What "bring your own backend" actually means

`McShadersAPI.registerBackend(...)` is real and documented, and a backend you contribute
is chosen by the same priority rules as the built-in one — there is no privileged path
for this mod's own renderer. If there were, that would be a bug in the framework.

But a backend is **Java that draws**. You receive a compiled `EffectGraph` — the layers
that survived capability filtering, with their parameters resolved and eased — and it is
yours to render however you like. The framework hands you *what* to draw and never *how*.
Loading GLSL, compiling it, binding uniforms and running passes would all be yours to
write.

So today this is a framework for **routing looks to renderers**, not a framework for
**running your shader code**.

### What is missing, specifically

The piece that would turn "write a backend" into "drop in a `.fsh`" is a backend that
runs vanilla-style post-processing chains. Then bringing your own shader would mean
adding a chain JSON and a fragment shader to a resource pack, and naming it from a
binding — no Java at all.

Most of the offline half of that already exists and is tested:

- `core.glsl` — include resolution, cycle reporting, `#line` mapping so a driver error
  names the file a human has to edit
- `core.chain` — the chain and pass model
- `core.layout` — std140 placement, and reading a uniform block back out of GLSL
- `check/` — all of the above, as a CLI that gates a build

**What does not exist is the runtime that executes them.** That is M2's unbuilt half, and
it is blocked on something specific rather than on effort: the 26.2 entry points for
hooking a post-processing chain have not been read out of any source that compiles on
26.2, and this project does not guess at APIs. See
[RENDERING-26.2.md](RENDERING-26.2.md), which lists what was established and what was
not.

If you know where 26.2 exposes that, it is the single most useful thing anyone could
contribute — the layer above it is already built and waiting.

## The checker

Independent of everything above, and usable on packs that have nothing to do with this
mod.

A post-processing chain is bound to its shaders by naming convention and to its uniform
blocks by **byte offset**. Nothing in the normal toolchain checks either. A shader that
was renamed leaves a chain that loads, swallows its own error, and quietly does nothing.
A uniform block whose two declarations have drifted leaves a shader reading real numbers
out of the wrong places — no error anywhere, just wrong output.

```sh
cd check && ../gradlew run --args="/path/to/assets"
```

It reads text, needs no GPU and no game, exits non-zero on errors, and is published
separately as `mcshaders-check` so you can run it in CI without cloning this repo.

**What it cannot tell you:** whether the GLSL compiles. That needs a driver.

## How far this has actually got

**Everything above the game is built and tested. Nothing has been run in a game.**

That is the honest headline and it is worth being precise about, because "the tests
pass" and "it works" are very different claims here:

| | State |
|---|---|
| The framework (looks, conditions, merging, easing) | **Tested.** 585 tests, no Minecraft needed |
| Datapack loading and `/reload` | **Written, compile-verified.** Never observed loading a file |
| Fog reaching the frame | **Written, compile-verified.** Never observed changing a pixel |
| The in-game editor | **Compiles.** The screens have never been opened |
| Own post-processing chains | **Not built.** Entry points not established |
| Shader-pack coexistence (Iris etc.) | **Not built** |

CI compiles this against the real Minecraft 26.2 jar, so every API call in it exists.
That is genuinely worth something — it is not guessed. But a mixin that compiles has not
been shown to *apply*, and an event that compiles has not been shown to *fire*.

**The first launch is instrumented for exactly this.** After 200 frames the mod says in
the log which links of the chain ran, and for any that did not, what to go and look at.
On every transition it names which binding won. So the first person to run it gets a
diagnosis rather than a mystery.

If the fog does not move and the log says the chain is complete, one known question
remains: `FogData` carries two distance pairs and it is not settled which one a
dimension look should write. Both the choice and the argument against it are recorded in
`FogApply`, and swapping them is a one-line change affecting both loaders.

## Where to go next

| You want to | Read |
|---|---|
| Author a look in a datapack | This page, plus `datapack/` in the repo for working examples |
| Use it from your own mod | [USING_AS_A_LIBRARY.md](USING_AS_A_LIBRARY.md) |
| Write a backend that draws | [USING_AS_A_LIBRARY.md](USING_AS_A_LIBRARY.md#contributing-a-backend) — the contract, and what you receive per frame |
| Know what is planned, in order | [ROADMAP.md](ROADMAP.md) |
| Know what 26.2 changed | [RENDERING-26.2.md](RENDERING-26.2.md), [DATAPACKS-26.2.md](DATAPACKS-26.2.md) |
| Understand the module layout | [ARCHITECTURE.md](ARCHITECTURE.md) |
