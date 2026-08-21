// Root build. Per-target configuration lives in the loader subprojects; this file
// only carries what is genuinely common to all of them.
//
// Properties are read through `providers.gradleProperty` rather than the `by
// project` delegate, because the delegate keys off the *variable name* and these
// are snake_case in gradle.properties, per Minecraft modding convention.

plugins {
    base
}

val modGroup = providers.gradleProperty("mod_group").get()
val modVersion = providers.gradleProperty("mod_version").get()
val javaVersion = providers.gradleProperty("java_version").get().toInt()

allprojects {
    group = modGroup
    version = modVersion
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion)
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
