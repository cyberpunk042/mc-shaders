# `vanilla/` — vanilla Minecraft, no loader API

Not published. Bundled into both loader jars, the way `common` and `mcshaders-core` are.

## The rule

**May import `net.minecraft.*`. May not import `net.fabricmc.*` or `net.neoforged.*`.**

Anything touching a loader's own API stays in that loader's module.

## Why it exists

`common/` deliberately has no Minecraft dependency — it is published as
`mcshaders-api` for other mods to compile against, and dragging Minecraft into it would
make that artifact unusable outside the game.

That left nowhere for code that needs **vanilla and nothing else**: reading files out of
a `ResourceManager`, sampling a `Level`, writing a frame's `FogData`. Each loader would
have carried its own copy — two copies of the same vanilla calls, to be changed together
and silently diverging when they are not.

## What is in it

| | |
|---|---|
| `BindingScan` | Reads binding files out of a `ResourceManager` and puts them in force |
| `render/WorldSampler` | Turns a frame's level, camera, delta and render state into a `WorldState` |
| `fog/FogApply` | Writes the resolved fog into the frame's `FogData` |
| `gui/` | The editor screens, and which one the editor key should open |

Each loader keeps only the few lines that genuinely differ — registering a listener,
subscribing an event, binding a key. That is why Fabric and NeoForge behave identically
by construction rather than by being kept in step.

It matters most for `FogApply`: which of `FogData`'s two distance pairs a dimension look
should write is still an open question, and sharing the code means one in-game test
answers it for both loaders instead of one.

## How it gets Minecraft

ModDevGradle in **NeoForm mode** — the same `net.neoforged.moddev` plugin the neoforge
module uses, given `neoFormVersion` instead of `version`, which is its documented way to
compile against Minecraft without loader extensions.

Using a NeoForge-adjacent toolchain to build code Fabric also consumes is sound **only
because 26.1+ ships unobfuscated**: no mappings, no remapping, so both plugins see the
same real class names. On an obfuscated version this would have needed a remapping story
instead.
