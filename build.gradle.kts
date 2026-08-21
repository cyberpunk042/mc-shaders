// Root build. Per-target configuration lives in the loader subprojects; this file
// only carries what is genuinely common to all of them.
//
// Every version referenced here resolves from gradle.properties — see the header
// there for why the values are marked unverified until the first networked run.

plugins {
    base
}

val modGroup: String by project
val modVersion: String by project

allprojects {
    group = modGroup
    version = modVersion
}

subprojects {
    apply(plugin = "java")

    val javaVersion: String by project

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion.toInt())
        }
    }

    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<ProcessResources>().configureEach {
        // Loader metadata carries ${version}; keep it in step with the build.
        inputs.property("version", project.version)
        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
            expand("version" to project.version)
        }
    }
}
