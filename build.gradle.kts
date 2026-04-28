plugins {
    kotlin("jvm") version "2.3.21"
    application
    id("com.gradleup.shadow") version "9.4.1"
    id("com.diffplug.spotless") version "8.4.0"
}

group = "com.eventb"
version = "1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("de.hhu.stups:rodin-eventb-ast:3.8.0")
    implementation("de.hhu.stups:eventbstruct:2.15.4")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.json:json:20251224")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass.set("com.eventb.checker.MainKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        showExceptions = true
        showStackTraces = true
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.5.0")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.5.0")
    }
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("all")
    manifest {
        attributes(
            "Main-Class" to "com.eventb.checker.MainKt",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.register<Exec>("setupGitHooks") {
    description = "Configure git to use .githooks/ directory for hooks"
    group = "setup"
    commandLine("git", "config", "core.hooksPath", ".githooks")
    doLast {
        println("Git hooks configured: core.hooksPath = .githooks")
    }
}
