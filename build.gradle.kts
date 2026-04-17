plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "dev.driftn2forty"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.2.1")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.12.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

val runtimeClasspath = configurations.runtimeClasspath

tasks {
    
    test {
        useJUnitPlatform()
    }

    withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror"))
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    shadowJar {
        archiveClassifier.set("")

        configurations = runtimeClasspath.map { setOf(it) }

        dependencies {
            // Only merge bStats into the final jar, no other dependencies
            exclude { it.moduleGroup != "org.bstats" }
        }

        // Relocate bStats into the plugin's package to avoid conflicts with other plugins using bStats
        relocate("org.bstats", "${project.group}.blindspot.bstats")
    }

    build {
        dependsOn(shadowJar)
    }
}