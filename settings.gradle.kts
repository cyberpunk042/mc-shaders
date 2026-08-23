// ─────────────────────────────────────────────────────────────────────────────
// Build layout
//
//   core/                 pure Java, zero Minecraft — its own standalone build
//   check/                pure Java — reads shader packs and validates them
//   common/               loader- AND Minecraft-free; published as mcshaders-api
//   vanilla/              vanilla Minecraft, no loader API — shared by both loaders
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

// `core_version` is declared in core/gradle.properties, where the core build reads
// it, and again here, where common/, fabric/ and neoforge/ read it. Two
// declarations of one fact with a comment asking a human to keep them in step is
// the exact shape of drift this project's own tooling exists to catch, so the
// build checks it rather than trusting the comment.
run {
    val declared = providers.gradleProperty("core_version").get()
    val inCoreBuild = file("core/gradle.properties").readLines()
        .first { it.startsWith("core_version=") }
        .substringAfter("=")
        .trim()
    check(declared == inCoreBuild) {
        "core_version disagrees: gradle.properties says $declared, " +
            "core/gradle.properties says $inCoreBuild. They name the same artifact."
    }
}

rootProject.name = "mc-shaders"

includeBuild("core")
includeBuild("check")

include("common")
include("vanilla")
include("fabric")
include("neoforge")
