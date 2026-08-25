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

    // PortedFileLicenceTest reads the sources as text, so its subject is the comments
    // in them. Comments do not reach the class files, so without this the task stays
    // up-to-date across exactly the edit it exists to catch: a ported file losing its
    // relicence header re-runs nothing and the build stays green.
    inputs.files(fileTree("src/main/java"))
        .withPropertyName("mainSourcesAsText")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // LayerGeometryTest asserts the library guide still contains the examples it runs.
    // The guide lives in the outer repository, not this included build, and Gradle
    // cannot infer it — without this a doc-only edit re-runs nothing and a stale
    // example stays green, which is the one case that assertion exists for.
    inputs.file(rootDir.parentFile.resolve("docs/USING_AS_A_LIBRARY.md"))
        .withPropertyName("libraryGuide")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // ShapeRecipeDocTest.Page does the same for the shapes page, and additionally reads
    // its own source to take the example from the markers rather than from a snippet
    // list. Both files therefore have to be inputs, or the check that the two agree
    // goes up-to-date across an edit to either of them.
    inputs.file(rootDir.parentFile.resolve("docs/SHAPES.md"))
        .withPropertyName("shapesPage")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree("src/test/java"))
        .withPropertyName("testSourcesAsText")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // ApiSurfaceTest asserts this very file declares JOML as `api` rather than
    // `implementation`, because the difference decides whether a consumer of the
    // published artifact can call the methods that return a Vector3f. Gradle does not
    // treat a build script as an input to the tests it configures, so without this the
    // task goes UP-TO-DATE across exactly the edit the assertion exists to catch —
    // demote the dependency and the build stays green having run nothing.
    inputs.file("build.gradle.kts")
        .withPropertyName("buildScriptAsText")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Javadoc is published because this is a library other people read against.
java {
    withJavadocJar()
}

tasks.javadoc {
    // No `links(...)` to an external JDK javadoc: it makes the build reach the
    // network at build time, so an unreachable docs host fails the build for a
    // purely cosmetic cross-link.
    //
    // doclint runs for `reference` and `html`, as errors. Those are the two groups
    // that catch documentation which is wrong rather than merely thin: a {@link} to
    // a method that no longer exists, and a bare `<` that silently swallows the rest
    // of a sentence as an unclosed tag. Porting 13,000 lines in from another repo
    // brought eighteen of exactly those across, and nothing in the build would have
    // failed on the nineteenth.
    //
    // `missing` is deliberately not enabled: an undocumented parameter is a gap, not
    // a defect, and failing on it would make the gate noisy enough that someone
    // would switch the whole thing off.
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:reference,html", "-quiet")
        addBooleanOption("Werror", true)
    }
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
