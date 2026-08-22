# Datapack loading on 26.2 — what was confirmed

M3 needs the game to hand over a pack's files. This is the API that does it, read
out of a mod that compiles on 26.2 rather than remembered.

**Source.** [`Snownee/Jade`](https://github.com/Snownee/Jade/tree/26.2-fabric) at
`26.2.11`, which registers three reload listeners and subclasses the vanilla JSON
one. Every signature below was read from its source. Nothing here has been
compiled by this repository yet — that happens with the first M3 loader commit.

Companion to [RENDERING-26.2.md](RENDERING-26.2.md), which covers the render path.

## The chain

| Piece | Signature |
|---|---|
| Fabric registration | `net.fabricmc.fabric.api.resource.v1.ResourceLoader`<br>`ResourceLoader.get(PackType).registerReloadListener(Identifier, PreparableReloadListener)` |
| Pack type | `net.minecraft.server.packs.PackType.SERVER_DATA` (datapacks) / `CLIENT_RESOURCES` |
| Base interface | `net.minecraft.server.packs.resources.PreparableReloadListener` |
| Simple synchronous | `ResourceManagerReloadListener` → `void onResourceManagerReload(ResourceManager)` |
| Vanilla JSON | `SimpleJsonResourceReloadListener<T>` → constructor `(Codec<T>, FileToIdConverter)` |
| Directory naming | `FileToIdConverter.json("<directory>")` |

Two of those are migration hazards for anyone porting from 1.21.

**The Fabric entry point moved.** It is
`net.fabricmc.fabric.api.resource.v1.ResourceLoader` — a **v1** package — and the
registration takes an `Identifier` alongside the listener. The 1.21.x idiom is
`ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(listener)`,
a different class, a different package, and a different argument list. Searching
for this turns up the old one.

**`SimpleJsonResourceReloadListener` is generic now.** On 26.2 it is
`SimpleJsonResourceReloadListener<T>` and its constructor takes a
`com.mojang.serialization.Codec<T>` plus a `FileToIdConverter`, so it hands the
subclass **decoded objects**. In 1.21.x it took a `Gson` and a directory name and
handed over `Map<ResourceLocation, JsonElement>` — raw JSON.

## What that decides for us

It decides which listener M3 uses, and the answer is not the JSON one.

`SimpleJsonResourceReloadListener` on 26.2 wants a DFU codec. Our binding format
is read by [`BindingCodec`](../common/src/main/java/net/cyberpunk042/mcshaders/codec/BindingCodec.java),
which is gson-based and deliberately outside `core` so the published library
carries no JSON dependency. Those do not compose: adopting the vanilla listener
would mean writing a second, DFU-flavoured codec for the same format and keeping
the two in agreement — a duplication with a drift bug built into it.

So M3 uses the plain `ResourceManagerReloadListener`, reads the files itself, and
hands their contents to
[`BindingLoader`](../common/src/main/java/net/cyberpunk042/mcshaders/codec/BindingLoader.java),
which already takes exactly that shape. `BindingLoader` was designed to that shape
before this was read, so this is a confirmation rather than a plan.

The cost is stated rather than hidden: we do not get vanilla's per-file decoding
and error reporting. We do not want them either — `BindingLoader` skips a broken
file and reports it by name, which is the behaviour we chose on purpose, and a
listener that fails the reload wholesale would undo it.

## What is still missing

Reading the files out of a `ResourceManager` — the method that lists a directory
and opens each entry — has not been read from source. Jade's listeners are handed
their resources by the vanilla JSON base class or do not read files at all, so the
`ResourceManager` methods themselves are the one link in this chain still unread.

Everything above it is established.
