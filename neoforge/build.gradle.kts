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
    implementation(project(":common"))
    jarJar("net.cyberpunk042:mcshaders-core:$coreVersion")
}
