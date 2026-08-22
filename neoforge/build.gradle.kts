plugins {
    id("net.neoforged.moddev")
}

val mcVersion = providers.gradleProperty("mc_version").get()
val coreVersion = providers.gradleProperty("core_version").get()

fun versioned(suffix: String): String =
    providers.gradleProperty("mc_${mcVersion.replace('.', '_')}_$suffix").get()

neoForge {
    version = versioned("neoforge")

    runs {
        create("client") { client() }
        create("server") { server() }
    }

    mods {
        create("mcshaders") {
            sourceSet(sourceSets["main"])
        }
    }
}

dependencies {
    // `implementation` compiles against it; `jarJar` is what actually packages it.
    // Declaring only the first shipped a jar missing every class in common while
    // this module's entrypoint imported them. See
    // gradle/verify-jar-contents.gradle.kts.
    implementation(project(":common"))
    jarJar(project(":common"))

    jarJar("net.cyberpunk042:mcshaders-core:$coreVersion")
}

apply(from = rootProject.file("gradle/verify-jar-contents.gradle.kts"))
