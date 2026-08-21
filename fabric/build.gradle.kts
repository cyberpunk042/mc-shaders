plugins {
    id("fabric-loom")
}

// Stonecutter tells us which Minecraft version this target is for; the matching
// loader coordinates are looked up from the root gradle.properties by key, so
// adding a Minecraft version never means editing a build script.
val mcVersion: String = stonecutter.current.version

fun versioned(suffix: String): String =
    providers.gradleProperty("mc_${mcVersion.replace('.', '_')}_$suffix").get()

val coreVersion: String by project

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
