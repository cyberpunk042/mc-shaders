plugins {
    id("net.neoforged.moddev")
}

val mcVersion: String = stonecutter.current.version

fun versioned(suffix: String): String =
    providers.gradleProperty("mc_${mcVersion.replace('.', '_')}_$suffix").get()

val coreVersion: String by project

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
    implementation(project(":common"))
    jarJar("net.cyberpunk042:mcshaders-core:$coreVersion")
}
