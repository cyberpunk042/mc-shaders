// Minecraft-facing code shared by every loader, and the public API surface other
// mods compile against (McShadersAPI).
//
// It depends on the pure-Java core through the included build (see
// settings.gradle.kts), so the framework logic is written once and consumed
// identically by Fabric and NeoForge.

plugins {
    `java-library`
    `maven-publish`
}

val coreVersion = providers.gradleProperty("core_version").get()

dependencies {
    // The version is nominal: `includeBuild("core")` substitutes this coordinate
    // with the local project by group and module name. It is stated anyway so the
    // declaration is valid on its own terms and readable without knowing that.
    api("net.cyberpunk042:mcshaders-core:$coreVersion")
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "mcshaders-api"
            pom {
                name = "MC Shaders API"
                description = "The API other Minecraft mods compile against to contribute effects, backends and dimension bindings to MC Shaders."
            }
        }
    }
}

apply(from = rootProject.file("gradle/publishing.gradle.kts"))
