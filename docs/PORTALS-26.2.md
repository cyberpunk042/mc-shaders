# Portals and dimension access on 26.2 — what was confirmed

Getting a player into another dimension. Read out of a mod that compiles on 26.2
rather than remembered.

**Source.** [`GreencraftMC/NeoTeleport`](https://github.com/GreencraftMC/NeoTeleport)
at `1.2.1`, whose Fabric module declares `minecraft "com.mojang:minecraft:26.2"`,
`fabric-loader:0.19.3`, and a `fabric.mod.json` depending on `minecraft: "~26.2"`
and `java: ">=25"`. That loader version is the same one
[VERSIONS.md](VERSIONS.md) pins and the same one Jade pins — three independent
mods agreeing on it.

Companions: [RENDERING-26.2.md](RENDERING-26.2.md) for the render path,
[DATAPACKS-26.2.md](DATAPACKS-26.2.md) for the reload path.

## The chain

| Piece | Signature |
|---|---|
| Parse a dimension id | `net.minecraft.resources.Identifier.tryParse(String)` |
| Make its key | `ResourceKey.create(Registries.DIMENSION, identifier)` |
| Resolve the level | `MinecraftServer#getLevel(ResourceKey<Level>)` → `ServerLevel` |
| Move the player | `Entity#teleport(TeleportTransition)` |
| The transition | `net.minecraft.world.level.portal.TeleportTransition` |
| Read current dimension | `entity.level().dimension().identifier()` |

Verbatim from the source, with the surrounding bookkeeping removed:

```java
Identifier dimensionId = Identifier.tryParse(dimension);
ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));

player.teleport(new TeleportTransition(
        level,
        new Vec3(x, y, z),
        Vec3.ZERO,                                  // delta
        yRot, xRot,
        TeleportTransition.PLACE_PORTAL_TICKET));
```

Two details a signature written from memory would get wrong.

**Teleporting takes a `TeleportTransition`, not coordinates.** One object carries
the destination level, position, delta, both rotations and a post-teleport action.
The older idiom of a `teleport(level, x, y, z, yaw, pitch)` overload is not what
this mod calls.

**Reading a dimension's id is `.identifier()`.** `entity.level().dimension()`
gives a `ResourceKey`, and its id accessor here is `identifier()` — the 1.21.x
`location()` is what a search turns up, and what a port from that era would write.

`TeleportTransition.PLACE_PORTAL_TICKET` is the post-teleport action used for a
portal-style move; it is a constant on the class, and it is the one a mod moving a
player between dimensions actually passes.

## What this decides for the Ancient City portal

The destination half is solved: given `mcshaders:beyond` as a string, the four
lines above put a player in it.

It also means the **frame geometry no longer blocks anything**, which was not
obvious. The chosen behaviour is "lit like a nether portal", and vanilla's nether
portal does not hardcode a shape — it scans outward from the ignition point for an
opening bounded by the frame material. Doing the same needs no knowledge of the
Ancient City's exact dimensions, which two secondary sources disagreed about and
which `minecraft.wiki` is unreachable from here to settle.

The gate is not the shape. Reinforced deepslate is unobtainable in survival and
immune to pistons, so the only frame anyone can light is one the game generated.

That scan is pure logic over a block predicate, so it lives in `core` and is
tested without a game — see `core.portal.FrameScan`. Its tests draw each world as
a picture and hold the scan to the claim above: every frame in them is a different
shape, and none of those shapes appear in the scan itself.

## What is still unread

Two links, and neither has a source here to read from. NeoTeleport is
command-driven: it registers no blocks and handles no block interaction.

- **The use event** — what fires when a player right-clicks the frame with an
  igniter.
- **Registering a block** — the portal block that fills the opening, and what
  happens when an entity is inside it.

Those are the next things to establish before any portal code is written, by the
same method: find a 26.2 mod that does them, and read it.
