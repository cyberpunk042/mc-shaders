# Contributing to MC Shaders++

The short version: **claims in this repository carry their evidence.** If you write
that something works, something should fail when it stops working. That is the whole
house style, and the rest of this page is what it means in practice.

## The most useful thing you could contribute

**Where 26.2 exposes post-processing chain execution.**

The offline half of running vanilla-style shader chains is built and tested — include
resolution with `#line` mapping, the chain and pass model, std140 uniform placement,
and a CLI that gates a build on all three. What is missing is the runtime that
executes them, and it is missing for one specific reason: the 26.2 entry points have
not been read out of any source that compiles on 26.2, and this project does not guess
at APIs.

If you know where they are — ideally from a mod that ships on 26.2, or from Mojang-mapped
patch context — that unblocks the layer above it, which is already written. See
[docs/SHADERS.md](docs/SHADERS.md#can-i-bring-my-own-shaders).

## Building

The repository is three Gradle builds sharing one pinned wrapper. Which one you need
depends on what you are changing.

| What you changed | Command | Needs |
|---|---|---|
| `core/` — the framework, no Minecraft | `cd core && ../gradlew build` | **JDK 21** |
| `check/` — the shader-pack checker | `cd check && ../gradlew test` | **JDK 21** (plus `glslang-tools` for the compile tests, which skip without it) |
| `common/` — the Minecraft-facing API | `./gradlew :common:build --configure-on-demand` | **JDK 25** |
| `vanilla/`, `fabric/`, `neoforge/` | `./gradlew build` | **JDK 25** and network access to the Fabric and NeoForged Maven hosts |

`core` runs `build` rather than `test` on purpose: it publishes a javadoc jar, and its
doclint gate fails on a `{@link}` that resolves to nothing. Running only `test` leaves
that gate wired to nothing.

If you have no JDK 25, you can still work on `core` and `check` — they are pure Java and
authoritative, and between them they hold most of the logic.

## What a good change looks like

**Verified, not remembered.** Minecraft API details in particular: read them out of
something that compiles on the target version — a mod that ships on it, or Mojang-mapped
patch context — and say in the commit message where they came from. `docs/RENDERING-26.2.md`
and `docs/DATAPACKS-26.2.md` are what that looks like when it is done.

**A test that fails without the change.** Then check it: break the fix on purpose and
confirm the test goes red *for the reason you expect*. A test that passes both ways is
worse than none, because it reads as coverage. Several defects in this repository were
found exactly this way, and at least one test was found to be asserting nothing — it
passed while the thing it named was deleted.

**Documentation that runs.** These tests read a page out of the repository and check
what it says: `BindingFormatDocTest`, `ReadmeExampleTest`, `LibraryDocExampleTest`,
`ShapeRecipeDocTest`, `FieldGuideExampleTest`, `LibraryGuideCoordinatesTest`,
`VersionMatrixDocTest`. If you add an example, add it to the document and let the test
parse it from there rather than keeping a copy in the test — a copy passes forever while
the page it came from drifts.

Two tests hold a copy instead, and are worth knowing about as the weaker form rather
than the pattern to follow. `LibraryApiDocExampleTest` runs the library guide's
`McShadersAPI` examples and is not pinned to the guide; its own javadoc has a section
on why neither pinning mechanism here fits it. `ReadmeExampleTest` in `core` is the same
shape — what guards the README's front-page Java example is still open. This paragraph
used to name `LibraryApiDocExampleTest` as an example of pinning, which is the drift
`ContributingGuideTest` now prevents.

**Honesty about what is unproven.** Much of this project compiles against the real 26.2
API and has never been observed running. That is stated plainly in the README and
`docs/SHADERS.md` rather than implied away, and a change should keep it that way. "It
compiles" and "it works" are different claims and the repository distinguishes them.

## What to avoid

- **Guessing at an API.** A plausible-looking signature that is wrong costs more than
  an admitted gap. If it cannot be verified, say so in the doc instead.
- **Widening a change beyond its reason.** A fix for one failure should be that fix.
- **Deleting a test to make a build green**, or asserting something weaker so it passes.
- **Claiming a status without the command output.** "Tests pass" is worth what the
  paste next to it is worth.

## Licensing

The engine is MIT — see [LICENSE](LICENSE). Code ported from `the-virus-block-mc` was
relicensed to MIT by its author, and every ported file says so in its header. If you
port more, carry that header across. Content that is *not* relicensed keeps its own
terms and the split is recorded in [docs/PORTING.md](docs/PORTING.md).

By contributing you agree your contribution is MIT-licensed.

## Where to start reading

| You want to | Read |
|---|---|
| Understand what this is | [docs/SHADERS.md](docs/SHADERS.md) |
| Author a look in a datapack | [docs/BINDINGS.md](docs/BINDINGS.md) |
| Use it from your own mod | [docs/USING_AS_A_LIBRARY.md](docs/USING_AS_A_LIBRARY.md) |
| See what is done and what is not | [docs/ROADMAP.md](docs/ROADMAP.md) |
| Find the module layout | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
