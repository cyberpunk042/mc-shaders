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

## Reading the files — the last link, now read

This was the one gap: Jade's listeners are handed their resources by the vanilla
JSON base class, so none of them showed the listing call itself. It came from
vanilla instead. NeoForge's patch for `SimpleJsonResourceReloadListener` carries
`scanDirectory` in its context lines — unprefixed, so Mojang-mapped 26.2 verbatim:

```java
for (Entry<Identifier, Resource> entry : lister.listMatchingResources(manager).entrySet()) {
    Identifier location = entry.getKey();
    Identifier id = lister.fileToId(location);

    try (Reader reader = entry.getValue().openAsReader()) {
```

| Piece | Signature |
|---|---|
| Build the lister | `FileToIdConverter.json(String directory)` |
| List a directory | `FileToIdConverter#listMatchingResources(ResourceManager)` → `Map<Identifier, Resource>` |
| File path → binding id | `FileToIdConverter#fileToId(Identifier)` → `Identifier` |
| Open one entry | `Resource#openAsReader()` → `Reader`, throws `IOException` |

Corroborated independently in Fabric API, which calls
`fileToIdConverter.listMatchingResources(state.resourceManager())` in `TagAliasLoader`,
and in NeoForge's own `DirectoryPalettedPermutations`.

**One file open at a time.** Vanilla opens each entry inside try-with-resources and
closes it before the next. `BindingLoader.loadReaders` would take readers directly,
but its signature wants every one of them open simultaneously — it was designed
before this API had been read, and a pack set is not bounded. So the listener follows
vanilla and reads each file to a string. `loadReaders` consequently still has no
caller.

The chain is complete: `ResourceLoader` → `ResourceManagerReloadListener` →
`listMatchingResources` → `openAsReader` → `BindingLoader` → `McShadersAPI.loadBindings`.
Implemented in
[`BindingReloadListener`](../fabric/src/main/java/net/cyberpunk042/mcshaders/fabric/data/BindingReloadListener.java).

## What this does not cover

**Dedicated servers.** Bindings are `data/` content, so the reload is server-side and
the registry it fills is a static in that JVM. In singleplayer and LAN the integrated
server shares the client's JVM and it works. On a dedicated server the reload runs
where nothing renders, and the connecting client never receives those files. Syncing
them over the network is not written, and nothing here should read as though it were.

**NeoForge.** The listener above is Fabric-only; `ResourceLoader` is Fabric API. The
vanilla half — the lister, the converter, `openAsReader` — is loader-neutral and
would be reused as-is, but it cannot live in `common`, which has no Minecraft
dependency by design.
