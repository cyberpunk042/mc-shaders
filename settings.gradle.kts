// ─────────────────────────────────────────────────────────────────────────────
// Build layout
//
//   core/                 pure Java, zero Minecraft — its own standalone build
//   common/               Minecraft-facing code shared by every loader
//   fabric/, neoforge/    per-loader entrypoints and packaging
//
// Multiversion is Stonecutter's job: it derives one subproject per entry in
// `active_versions` (see gradle.properties), so 26.3 becomes a property change
// rather than a branch.
//
// The core is an *included build* rather than a subproject on purpose: it must
// stay buildable and testable with a plain JDK and no Minecraft toolchain, which
// is what lets its 52 tests run anywhere, including environments that cannot
// reach the Minecraft Maven hosts at all.
// ─────────────────────────────────────────────────────────────────────────────

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
}

rootProject.name = "mc-shaders"

includeBuild("core")

val activeVersions: List<String> = (extra.properties["active_versions"] as? String ?: "26.2")
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)

val loaders = listOf("fabric", "neoforge")

stonecutter {
    create(rootProject) {
        for (mcVersion in activeVersions) {
            for (loader in loaders) {
                // Target name carries both axes, e.g. "26.2-fabric", so a build
                // failure names exactly which combination broke.
                vers("$mcVersion-$loader", mcVersion)
            }
        }
        vcsVersion = activeVersions.first() + "-fabric"
    }
}
