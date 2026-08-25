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
    //
    // `api` rather than `implementation`, because the entry point takes a gson type:
    // `PostChainCodec.read(JsonObject)`. Under `implementation` gson lands in the
    // published POM at runtime scope, and a consumer cannot compile a call to EITHER
    // overload — javac resolves the whole overload set, so even `read(Reader)` fails
    // with `cannot access JsonObject`. ApiSurfaceTest holds this.
    api("com.google.code.gson:gson:2.11.0")

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

    // ApiSurfaceTest asserts this very file declares gson as `api`. Gradle does not
    // treat a build script as an input to the tests it configures, so without this the
    // task goes UP-TO-DATE across exactly the edit the assertion exists to catch.
    inputs.file("build.gradle.kts")
        .withPropertyName("buildScriptAsText")
        .withPathSensitivity(PathSensitivity.RELATIVE)
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
