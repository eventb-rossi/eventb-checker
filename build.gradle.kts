plugins {
    kotlin("jvm") version "2.4.10"
    application
    id("com.gradleup.shadow") version "9.6.1"
    id("com.diffplug.spotless") version "8.10.0"
}

group = "com.eventb"
version = "1.13"
val projectVersion = version.toString()

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
    implementation("de.hhu.stups:eventbstruct:2.16.0")
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.json:json:20260814")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass.set("com.eventb.checker.MainKt")
}

tasks.test {
    useJUnitPlatform()
    // Emulate the JDK 24+ default XML depth limit so depth-related tests bite on JDK 21 too.
    systemProperty("jdk.xml.maxElementDepth", "100")
    // ValidationScriptsTest shells out to these, so they are real test inputs: without
    // declaring them the task stays UP-TO-DATE when only a script changed, and the build
    // cache would let CI skip the very tests that guard them.
    inputs.files(
        "scripts/validate-models-core.sh",
        ".github/scripts/validate-models.sh",
        ".gitlab/scripts/validate-models.sh",
    ).withPropertyName("validationScripts").withPathSensitivity(PathSensitivity.RELATIVE)
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
        attributes("Implementation-Version" to projectVersion)
    }
}

tasks.processResources {
    inputs.property("version", projectVersion)
    filesMatching("eventb-checker-version.properties") {
        expand("version" to projectVersion)
    }
}

tasks.shadowJar {
    archiveClassifier.set("all")
    manifest {
        attributes(
            "Main-Class" to "com.eventb.checker.MainKt",
            "Implementation-Version" to projectVersion,
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
