# `check/` — a shader-pack checker that needs no game

Runs on any pack, including ones that have nothing to do with this mod.

```sh
cd check && ../gradlew run --args="/path/to/assets"
```

That is the way to run it today. It **will** be published as
**`net.cyberpunk042:mcshaders-check`** — the build produces a runnable distribution
(`bin/mcshaders-check`, launch scripts and dependencies) and the release job pushes it —
but no release has been cut, so nothing is on GitHub Packages yet. `../gradlew installDist`
gives you the same distribution locally in the meantime.

## The problem it solves

A Minecraft post-processing chain is bound to its shaders **by naming convention** and
to its uniform blocks **by byte offset**. Nothing in the normal toolchain checks either.

- Rename a shader and the chain still loads, catches its own exception, and silently
  does nothing.
- Let two declarations of a uniform block drift apart and the shader reads real numbers
  out of the wrong places.

Neither produces an error anywhere. Both look like "the effect just isn't working".

## What it reports

Walks every `post_effect/*.json` beneath the directory given and reports missing
shaders, unresolved includes, targets read before they are written, targets nothing
reads, inputs whose sampler the shader does not declare, and uniform blocks the
pipeline and the shader disagree about — **with the byte offset where they start to
disagree**.

Exits non-zero on errors so it can gate a build. Warnings and notes print without
failing.

## What it cannot tell you

**Whether the GLSL compiles.** That needs a driver. This reads text.

## Why it is its own build

No Minecraft dependency and no Minecraft toolchain, deliberately: a pack should be
checkable in someone else's CI, by someone with no interest in building this mod. It is
published as a runnable distribution so using it does not mean cloning this repository.

## Seen in anger

Run against `the-virus-block-mc`'s packs it found four chains naming archived shaders,
one uniform block that thirteen chains share and disagree with, and several hosts that
stopped being extended when their shader was. That report — including the findings that
look alarming and are correct by design — is
[../docs/VIRUS-BLOCK-SHADER-STATE.md](../docs/VIRUS-BLOCK-SHADER-STATE.md).
