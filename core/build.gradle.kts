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
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "mcshaders-core"
        }
    }
}
