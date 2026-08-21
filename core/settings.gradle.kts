// Standalone build for the pure-Java framework core.
//
// The core deliberately has ZERO Minecraft dependencies, so it builds and tests
// on any plain JDK without the Minecraft toolchain. The Minecraft layer
// (../common, ../fabric, ../neoforge) consumes it through an included build.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "mcshaders-core"
