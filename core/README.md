# `core/` — the framework, with no Minecraft in it

Published as **`net.cyberpunk042:mcshaders-core`**. Usable in any JVM project.

```kotlin
implementation("net.cyberpunk042:mcshaders-core:0.2.0")
```

## What it is

The whole model of *what a frame should look like*, and none of the drawing. Effects,
their parameters, how looks layer and merge, the conditions that gate them, the easing
between them, and the seam a renderer plugs into.

It has exactly one dependency — JOML, for maths — and **no Minecraft**. That is not
tidiness. It is why the suite runs on a bare JDK with no game, no GPU and no network,
and why "the framework is tested" is a claim that can be made honestly while the
rendering above it has never been run.

```sh
cd core && ../gradlew test
```

## What lives here

| Package | |
|---|---|
| `param`, `effect` | Parameter values with interpolation; effect layers and stacks |
| `binding` | Dimension bindings and the condition algebra |
| `transition` | Easing, including retargeting mid-blend |
| `backend` | The renderer seam, and a no-op implementation |
| `glsl` | Include resolution, cycle reporting, `#line` source mapping |
| `layout`, `chain` | std140 placement; post-chain modelling — what `check/` is built on |
| `schema`, `edit` | What is tunable about an effect; edit sessions with undo |
| `shape`, `mesh`, `pattern`, `field` … | The geometry half, ported from `the-virus-block-mc` |

The geometry packages came across with characterisation tests they did not have
before — see [../docs/PORTING.md](../docs/PORTING.md) for what moved and the licence
split. Some of that surface is not yet reached by anything: it is waiting on the field
system, not abandoned.

## Why it is a separate build

`core/` and `check/` are **included builds**, not subprojects, so they stay buildable
with a plain JDK and no Minecraft toolchain — including in environments that cannot
reach the Minecraft Maven hosts at all.

## The rule

**No `net.minecraft` imports, ever.** The first convenience import costs the
testability that everything else here depends on. Minecraft-facing code goes in
`common/` (published API, still no Minecraft) or `vanilla/` (Minecraft, no loader).

See [../docs/USING_AS_A_LIBRARY.md](../docs/USING_AS_A_LIBRARY.md) — its examples are
run as tests.
