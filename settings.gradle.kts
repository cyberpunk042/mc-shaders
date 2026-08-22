// ─────────────────────────────────────────────────────────────────────────────
// Build layout
//
//   core/                 pure Java, zero Minecraft — its own standalone build
//   check/                pure Java — reads shader packs and validates them
//   common/               Minecraft-facing code shared by every loader
//   fabric/, neoforge/    per-loader entrypoints and packaging
//
// core/ and check/ are *included builds* rather than subprojects on purpose: they
// must stay buildable and testable with a plain JDK and no Minecraft toolchain,
// which is what lets their tests run anywhere — including environments that cannot
// reach the Minecraft Maven hosts at all. For check/ that is not merely
// convenient: a shader pack should be validatable in someone else's CI, by someone
// who has no interest in building this mod.
//
// On multiversion: see docs/VERSIONS.md. The per-version property keys and the
// `mc_version` selector are already in place, so the build reads its Minecraft
// coordinates by version rather than hardcoding them. Stonecutter is the intended
// mechanism for building several versions at once, and is deliberately not wired
// yet — 26.3 does not exist, so today's matrix has exactly one entry, and a
// half-wired preprocessor would be complexity with no present payoff.
// ─────────────────────────────────────────────────────────────────────────────

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
        mavenCentral()
        gradlePluginPortal()
    }

    // Plugin versions are declared once here so the loader build scripts can apply
    // them by id alone. A `plugins {}` block cannot interpolate a property, so
    // centralising them is what keeps gradle.properties the single source of truth.
    plugins {
        id("net.fabricmc.fabric-loom") version providers.gradleProperty("fabric_loom_version").get()
        id("net.neoforged.moddev") version providers.gradleProperty("moddevgradle_version").get()
    }
}

rootProject.name = "mc-shaders"

includeBuild("core")
includeBuild("check")

include("common")
include("fabric")
include("neoforge")
