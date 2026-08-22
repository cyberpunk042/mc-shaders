plugins {
    `java-library`
    application
}

group = "net.cyberpunk042"
version = providers.gradleProperty("check_version").getOrElse("0.1.0")

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
}

dependencies {
    api("net.cyberpunk042:mcshaders-core")

    // The dependency core is not allowed: this module exists precisely so that
    // parsing lives outside the engine. See docs/PORTING.md.
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "net.cyberpunk042.mcshaders.check.ShaderCheck"
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}
