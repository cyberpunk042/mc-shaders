// Vanilla Minecraft, no loader API.
//
// `common` deliberately has no Minecraft dependency: it is published as
// mcshaders-api for other mods to compile against, and dragging Minecraft into it
// would make that artifact unusable outside the game. But that left nowhere to put
// code which needs vanilla and nothing else — reading files out of a
// ResourceManager, sampling a Level — so each loader had to carry its own copy.
// Two copies of the same vanilla calls, to be changed together and silently
// diverging when they are not.
//
// This module is where that code goes instead. Both loaders depend on it and bundle
// it, exactly as they already do with `common` and `mcshaders-core`.
//
// The rule for what belongs here: it may import net.minecraft.*, and it may NOT
// import net.fabricmc.* or net.neoforged.*. Anything touching a loader's own API
// stays in that loader's module.

plugins {
    `java-library`
    // ModDevGradle in NeoForm mode. Same plugin the neoforge module uses, but given
    // `neoFormVersion` instead of `version`, which is its documented way to compile
    // against Minecraft "without any loader-specific extensions".
    //
    // Using the NeoForge-adjacent toolchain to build code that Fabric also consumes
    // is sound here only because 26.1+ ships unobfuscated: there are no mappings and
    // Loom does not remap, so both plugins see the same real class names and the
    // bytecode links either way. On an obfuscated version this would not work and
    // the module would have needed a remapping story. See docs/VERSIONS.md.
    id("net.neoforged.moddev")
}

val mcVersion = providers.gradleProperty("mc_version").get()

fun versioned(suffix: String): String =
    providers.gradleProperty("mc_${mcVersion.replace('.', '_')}_$suffix").get()

neoForge {
    neoFormVersion = versioned("neoform")
}

dependencies {
    // `api`, not `implementation`: the loaders call McShadersAPI directly, and this
    // module's own signatures hand back BindingLoader.Result.
    api(project(":common"))
}

java {
    withSourcesJar()
}
