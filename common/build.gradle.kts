// Minecraft-facing code shared by every loader.
//
// It depends on the pure-Java core through the included build (see
// settings.gradle.kts), so the framework logic is written once and consumed
// identically by Fabric and NeoForge.

plugins {
    `java-library`
}

val coreVersion: String by project

dependencies {
    // The version is nominal: `includeBuild("core")` substitutes this coordinate
    // with the local project by group and module name. It is stated anyway so the
    // declaration is valid on its own terms and readable without knowing that.
    api("net.cyberpunk042:mcshaders-core:$coreVersion")
}
