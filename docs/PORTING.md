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
- **Gson stays out of `core`.** JSON binding belongs in a module above it. Keeping
  the model free of serialisation concerns is what lets the same model be loaded
  from a datapack, a config file, or constructed in code — and under the licence
  split, loading external content is a first-class requirement.

## Order of extraction

Each step is independently useful and independently verifiable.

1. **GLSL include resolution** ✅ — the expansion the shader corpus depends on.
2. **Shape maths foundation** ✅ — `ShapeMath`, `SimplexNoise`, shape state and the
   shape vocabulary enums. Pure maths, now under test for the first time.
3. **Shape model** — the concrete shapes, with JSON binding split out.
4. **Field model: links and primitives** — the wires: follow, mirror, phase offset,
   radius match, orbit sync. `LinkResolver` is the substance.
5. **Effect and visual config model** — the contract between GUI, engine and GLSL.
6. **Rendering backend** — the first backend that actually draws.
7. **GUI framework** — the customiser, loader-neutral.
8. **the-virus-block-mc consumes the library.**

## Note on tests

The ported maths arrived with no tests. Characterisation tests were written against
it as it moved, which immediately paid for itself: the first attempt asserted that a
spheroid with equal `radius` and `length` is a sphere. It is not — `length` is a
*ratio*, so the sphere case is `length == 1`, and the deformation is
volume-preserving (`a²c = r³`). The tests now pin that invariant down, which is more
useful than what was originally asserted.

Porting untested code without writing tests as it moves means carrying its unknowns
across a version boundary and finding out later which of them mattered.
