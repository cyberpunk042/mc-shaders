plugins {
    id("fabric-loom")
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
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${versioned("fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${versioned("fabric_api")}")

    implementation(project(":common"))

    // Bundled rather than depended upon: the core is our own code with no
    // independent release cycle, so shipping it inside the jar spares users a
    // second download.
    include("net.cyberpunk042:mcshaders-core:$coreVersion")
}

loom {
    splitEnvironmentSourceSets()
    mods {
        create("mcshaders") {
            sourceSet(sourceSets["main"])
        }
    }
}
