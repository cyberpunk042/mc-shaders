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

    // `implementation`, not `api`: parsing is this module's business, not something
    // consumers compile against. It costs nothing at runtime under a loader —
    // Minecraft already provides gson, which is checked rather than assumed: Jade
    // imports com.google.gson in two files and declares no gson dependency.
    // The version matches check/, which parses shader chains with the same library.
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
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
