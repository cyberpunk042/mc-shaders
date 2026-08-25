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

    // `api`, not `implementation`, because three public methods hand a gson type
    // back: FieldCodec.write, BindingCodec.write and BindingCodec.writeAll all
    // return JsonObject or JsonArray. That makes gson part of what consumers compile
    // against whether or not this module wants it to be.
    //
    // It used to say `implementation`, reasoning that "parsing is this module's
    // business, not something consumers compile against". The surface disagreed:
    // under `implementation` gson lands in the published POM at runtime scope, so a
    // consumer of mcshaders-api could not compile the example on line 613 of
    // USING_AS_A_LIBRARY.md — `javac` fails with "cannot access JsonObject". Adding
    // gson to that classpath fixes it, which is the whole proof.
    //
    // ApiSurfaceTest pins this: gson is the only non-JDK, non-project package in the
    // public API, and it must be declared here as `api`.
    //
    // Still true, and still the reason a runtime dependency costs nothing under a
    // loader: Minecraft already provides gson — checked rather than assumed, since
    // Jade imports com.google.gson in two files and declares no gson dependency.
    // The version matches check/, which parses shader chains with the same library.
    api("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }

    // Several tests here read files out of the repository rather than the classpath:
    // the shipped pack, and the pages that ReadmeExampleTest, BindingFormatDocTest,
    // VersionMatrixDocTest and the rest parse straight out of the document. None of
    // those are inputs Gradle can infer, so editing a page and running the tests would
    // report green without having re-read it — the one case the doc tests exist for.
    //
    // docs/ is taken whole rather than named file by file: StatedTestCountsTest scans
    // every page for a test count, so any of them can be the one that fails, including
    // a page added after this line was written.
    inputs.files(
        rootProject.fileTree("datapack"),
        rootProject.file("README.md"),
        rootProject.file("CONTRIBUTING.md"),
        rootProject.fileTree("docs") { include("*.md") },
        rootProject.file("gradle.properties"),
        rootProject.file("core/gradle.properties"),
        rootProject.file("common/build.gradle.kts"),
        rootProject.file("check/build.gradle.kts"),
    ).withPropertyName("documentsUnderTest")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // FieldContentScanTest reads a content tree that is deliberately not in this
    // repository, named by this property. Gradle hands `-D` to the build JVM and not
    // to the test JVM, so without this the documented command would run the scan
    // against nothing and report a pass by skipping it — the same silent-zero the
    // test's own assertion exists to prevent, one level up.
    //
    // Declared as an input as well as forwarded, so pointing it somewhere new re-runs
    // the task instead of reporting the previous tree's result as UP-TO-DATE.
    val fieldContent = providers.systemProperty("mcshaders.fieldContent")
    inputs.property("fieldContent", fieldContent).optional(true)
    if (fieldContent.isPresent) {
        systemProperty("mcshaders.fieldContent", fieldContent.get())
    }
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
