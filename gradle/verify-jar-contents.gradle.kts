// Asserts that the mod jar actually contains the classes it needs at runtime.
//
// This exists because of a bug that shipped: `common` was declared
// `implementation(project(":common"))` on both loaders — compiled against, never
// packaged — so every jar built was missing McShaders, McShadersAPI,
// BuiltinEffects and the codec, while the loader entrypoints imported all of
// them. A real install would have died with NoClassDefFoundError on mod init.
//
// It survived four green CI runs because nothing about it is visible to a
// compiler. Dev runs put `common` on the classpath, so it works there; and
// building a jar succeeds whether or not the classes are inside it. Compiling is
// not packaging, and CI was only ever checking the first.
//
// So the fix needed a check, or it would be the same class of bug again — a
// declaration with nothing verifying it connects.
//
// The check looks for the CLASS, not for a jar filename. Filenames depend on how
// each loader's bundling plugin names a project dependency (`common-x.y.z.jar`?
// `mcshaders-api-x.y.z.jar`?), which is exactly the kind of assumption that put
// the bug here in the first place. The class path is the actual requirement.

val requiredClasses = listOf(
    "net/cyberpunk042/mcshaders/McShaders.class",
    "net/cyberpunk042/mcshaders/McShadersAPI.class",
    "net/cyberpunk042/mcshaders/BuiltinEffects.class",
    "net/cyberpunk042/mcshaders/codec/BindingCodec.class",
    "net/cyberpunk042/mcshaders/core/ShaderPipeline.class",
    // The `vanilla` module. It is a third jar-in-jar alongside common and core, so
    // it can go missing exactly the way common did — and a missing BindingScan means
    // /reload throws instead of loading bindings.
    "net/cyberpunk042/mcshaders/vanilla/BindingScan.class",
)

// The datapack has the same problem the classes had, one layer over: it is
// authored at the repository root and reaches the jar only because both loader
// builds add it as a resources source directory. Nothing about that is visible to
// a compiler either — delete the srcDir line and the build still succeeds, shipping
// a mod whose dimension simply does not exist.
//
// It also must NOT be satisfied by a nested jar. `common` is bundled as a library,
// and a library's data/ is never read as a datapack, so data found only inside a
// nested jar would pass a naive check while doing nothing in game. These are
// therefore required at the TOP level of the mod jar.
val requiredResources = listOf(
    "data/mcshaders/dimension/beyond.json",
    "data/mcshaders/dimension_type/beyond.json",
    "data/mcshaders/mcshaders/bindings/beyond_depths.json",
)

val verifyJarContents = tasks.register("verifyJarContents") {
    group = "verification"
    description = "Fails if the mod jar is missing classes or datapack files it needs at runtime."
    dependsOn(tasks.named("assemble"))

    doLast {
        val libs = layout.buildDirectory.dir("libs").get().asFile
        // listFiles(): the lambda overload is ambiguous between FileFilter and
        // FilenameFilter, and this script cannot be compiled locally to find out
        // which one Kotlin picks.
        val candidates = (libs.listFiles()?.toList().orEmpty()).filter { file ->
            file.name.endsWith(".jar")
                && !file.name.endsWith("-sources.jar")
                && !file.name.endsWith("-javadoc.jar")
        }

        check(candidates.isNotEmpty()) { "No jar was produced in $libs" }

        // The mod jar is the biggest one: bundling makes it larger than any
        // intermediate the loader plugin leaves behind.
        val jar = candidates.maxByOrNull { it.length() }!!

        val found = mutableSetOf<String>()
        val foundResources = mutableSetOf<String>()
        val nestedJars = mutableListOf<String>()

        java.util.zip.ZipFile(jar).use { outer ->
            for (entry in outer.entries()) {
                if (!entry.name.endsWith(".jar")) {
                    if (entry.name in requiredClasses) {
                        found += entry.name
                    }
                    // Top level only — see the note on requiredResources.
                    if (entry.name in requiredResources) {
                        foundResources += entry.name
                    }
                    continue
                }
                nestedJars += entry.name
                // Bundled dependencies are jars inside the jar, so the classes we
                // need are usually one level down rather than loose.
                outer.getInputStream(entry).use { raw ->
                    java.util.zip.ZipInputStream(raw).use { inner ->
                        while (true) {
                            val nested = inner.nextEntry ?: break
                            if (nested.name in requiredClasses) {
                                found += nested.name
                            }
                        }
                    }
                }
            }
        }

        val missingResources = requiredResources - foundResources
        if (missingResources.isNotEmpty()) {
            error(
                buildString {
                    appendLine("${jar.name} is missing ${missingResources.size} datapack file(s):")
                    missingResources.forEach { appendLine("  - $it") }
                    appendLine()
                    append(
                        "These must be at the TOP level of the mod jar. A jar-in-jar library's "
                            + "data/ is never read as a datapack, so bundling is not enough - the "
                            + "loader build needs a resources srcDir pointing at datapack/."
                    )
                }
            )
        }

        val missing = requiredClasses - found
        if (missing.isNotEmpty()) {
            // Diagnostic rather than a bare failure: if the assumption about where
            // bundled classes live is wrong, this says what was actually there, so
            // it costs one CI round trip to correct rather than several.
            error(
                buildString {
                    appendLine("${jar.name} is missing ${missing.size} class(es) it needs at runtime:")
                    missing.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Nested jars found (${nestedJars.size}):")
                    if (nestedJars.isEmpty()) {
                        appendLine("  (none — nothing is being bundled at all)")
                    } else {
                        nestedJars.forEach { appendLine("  - $it") }
                    }
                    appendLine()
                    append(
                        "A dependency declared `implementation` is compiled against but not "
                            + "packaged. Bundling needs `include(...)` on Fabric or "
                            + "`jarJar(...)` on NeoForge."
                    )
                }
            )
        }

        logger.lifecycle(
            "verifyJarContents: ${jar.name} carries all ${requiredClasses.size} required classes"
                + " across ${nestedJars.size} nested jar(s),"
                + " and all ${requiredResources.size} datapack file(s) at the top level"
        )
    }
}

tasks.named("check") { dependsOn(verifyJarContents) }
