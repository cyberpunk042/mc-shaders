plugins {
    `java-library`
    application
    `maven-publish`
}

group = "net.cyberpunk042"
version = providers.gradleProperty("check_version").getOrElse("0.1.0")

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
}

// The core's version, read from the core build rather than restated here. A
// composite build substitutes the local project for this coordinate during
// development, so a wrong or missing version is invisible until someone consumes
// the published POM — which is exactly when it is most expensive to discover.
val coreVersion: String = file("../core/gradle.properties").readLines()
    .first { it.startsWith("core_version=") }
    .substringAfter("=")
    .trim()

dependencies {
    api("net.cyberpunk042:mcshaders-core:$coreVersion")

    // The dependency core is not allowed: this module exists precisely so that
    // parsing lives outside the engine. See docs/PORTING.md.
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "net.cyberpunk042.mcshaders.check.ShaderCheck"
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "mcshaders-check"

            // The jar alone is not the deliverable: this is a command-line tool, and
            // a consumer who has to work out its classpath has not really been given
            // it. The distribution carries the launch scripts and dependencies.
            artifact(tasks.named("distZip"))

            pom {
                name = "MC Shaders Checker"
                description = "Validates shader chains without the game: shader and include "
                        .plus("resolution, target usage, sampler binding, std140 uniform-block ")
                        .plus("layout, and GLSL compilation where a validator is available.")
            }
        }
    }
}

apply(from = rootDir.parentFile.resolve("gradle/publishing.gradle.kts"))
