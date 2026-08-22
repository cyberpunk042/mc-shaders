// Standalone build, for the same reason core is one: the checker must run on a
// plain JDK with no Minecraft toolchain, so that a shader pack can be validated
// in CI by anyone, without the game.
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

rootProject.name = "mcshaders-check"

includeBuild("../core")
