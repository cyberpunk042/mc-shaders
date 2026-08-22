plugins {
    // The *new* Loom plugin, required from Minecraft 26.1 onward. It does not
    // remap Minecraft or mods, because the game ships unobfuscated from 26.1 —
    // the legacy `fabric-loom` id fails with "Failed to find official mojang
    // mappings", since none are published for these versions.
    id("net.fabricmc.fabric-loom")
}

// The Minecraft coordinates are looked up by version key from the root
// gradle.properties, so adding a Minecraft version never means editing a build
// script — only adding its mc_<version>_* block and flipping mc_version.
val mcVersion = providers.gradleProperty("mc_version").get()
val coreVersion = providers.gradleProperty("core_version").get()

fun versioned(suffix: String): String =
    providers.gradleProperty("mc_${mcVersion.replace('.', '_')}_$suffix").get()

dependencies {
    minecraft("com.mojang:minecraft:${versioned("minecraft")}")
    // No mappings dependency: 26.1+ is unobfuscated and carries real names.

    // Plain `implementation`, not `modImplementation`: the 26.x Loom plugin does
    // not remap mods, so the remapping configurations no longer exist.
    implementation("net.fabricmc:fabric-loader:${versioned("fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${versioned("fabric_api")}")

    // Both `implementation` AND `include`. The first puts it on the compile
    // classpath; only the second puts it inside the jar. Declaring just the first
    // is what shipped a mod whose entrypoints imported classes that were not in
    // the artifact — it compiled, CI went green, and a real install would have
    // thrown NoClassDefFoundError on init. See gradle/verify-jar-contents.gradle.kts,
    // which now fails the build rather than letting that recur.
    implementation(project(":common"))
    include(project(":common"))

    // Bundled rather than depended upon: the core is our own code with no
    // independent release cycle, so shipping it inside the jar spares users a
    // second download.
    include("net.cyberpunk042:mcshaders-core:$coreVersion")
}

// The mod's datapack, authored once at the repository root and shipped by both
// loaders. It cannot live in `common`: that is bundled as a jar-in-jar library,
// and a library is not loaded as a mod, so its data/ is never read as a datapack.
// See datapack/README.md.
sourceSets["main"].resources.srcDir(rootProject.file("datapack"))

apply(from = rootProject.file("gradle/verify-jar-contents.gradle.kts"))

loom {
    // No splitEnvironmentSourceSets(): this mod keeps client and common code in
    // src/main, so there is no separate client source set to split out.
    mods {
        create("mcshaders") {
            sourceSet(sourceSets["main"])
        }
    }
}
