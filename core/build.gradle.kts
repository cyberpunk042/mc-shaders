plugins {
    `java-library`
    `maven-publish`
}

group = "net.cyberpunk042"
version = providers.gradleProperty("core_version").getOrElse("0.1.0")

java {
    // Deliberately below the Minecraft 26.x requirement (Java 25). The core is a
    // plain library: compiling it to an older release keeps it consumable by the
    // Minecraft modules AND buildable on any modern JDK, including CI images and
    // contributor machines that do not have a JDK 25 toolchain installed.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // The one allowed dependency class: pure-Java maths from Maven Central. Never
    // Minecraft, never a graphics API — see docs/PORTING.md.
    //
    // The version tracks Minecraft rather than latest. Minecraft 26.2 declares
    // `org.joml:joml:{strictly 1.10.8}`, and a strict constraint cannot be
    // satisfied by anything else — so publishing a newer JOML here makes the mod
    // modules unresolvable. Interop with Minecraft is the whole reason this
    // dependency exists, so matching its pin is the point, not a concession.
    api("org.joml:joml:1.10.8")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all,-serial")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Javadoc is published because this is a library other people read against.
java {
    withJavadocJar()
}

tasks.javadoc {
    // No `links(...)` to an external JDK javadoc: it makes the build reach the
    // network at build time, so an unreachable docs host fails the build for a
    // purely cosmetic cross-link.
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "mcshaders-core"
            pom {
                name = "MC Shaders Core"
                description = "Backend-neutral shader effect framework. Pure Java, no Minecraft dependency."
            }
        }
    }
}

apply(from = rootDir.parentFile.resolve("gradle/publishing.gradle.kts"))
