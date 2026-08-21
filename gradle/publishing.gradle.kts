// Shared publishing conventions.
//
// Applied by both builds via `apply(from = ...)`. Kept in one file so the POM
// metadata and the GitHub Packages coordinates cannot drift between the core and
// the mod modules.
//
// Credentials come from GITHUB_ACTOR/GITHUB_TOKEN (set automatically in Actions) or
// the gradle properties gpr.user/gpr.key for local publishing. Nothing is committed.

plugins.withId("maven-publish") {
    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/cyberpunk042/mc-shaders")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                        ?: providers.gradleProperty("gpr.user").orNull
                    password = System.getenv("GITHUB_TOKEN")
                        ?: providers.gradleProperty("gpr.key").orNull
                }
            }
        }

        publications.withType<MavenPublication>().configureEach {
            pom {
                url = "https://github.com/cyberpunk042/mc-shaders"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://github.com/cyberpunk042/mc-shaders/blob/main/LICENSE"
                    }
                }
                developers {
                    developer {
                        id = "cyberpunk042"
                        name = "cyberpunk042"
                    }
                }
                scm {
                    url = "https://github.com/cyberpunk042/mc-shaders"
                    connection = "scm:git:https://github.com/cyberpunk042/mc-shaders.git"
                }
            }
        }
    }
}
