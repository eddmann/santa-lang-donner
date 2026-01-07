plugins {
    application
    id("org.panteleyev.jpackageplugin")
    id("com.gradleup.shadow")
    id("org.jetbrains.kotlin.plugin.serialization")
}

application {
    mainClass.set("santa.cli.MainKt")
    applicationName = "santa-cli"
}

// Generate BuildConfig with version from root project
tasks.register("generateBuildConfig") {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    outputs.dir(outputDir)

    doLast {
        val buildConfigFile = outputDir.get().file("santa/cli/BuildConfig.kt").asFile
        buildConfigFile.parentFile.mkdirs()
        buildConfigFile.writeText("""
            package santa.cli

            object BuildConfig {
                const val VERSION = "${rootProject.version}"
            }
        """.trimIndent())
    }
}

sourceSets {
    main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/buildconfig"))
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateBuildConfig")
}

dependencies {
    implementation(project(":compiler"))
    implementation(project(":runtime"))
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

// Shadow JAR for fat JAR distribution
tasks.shadowJar {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "santa.cli.MainKt"
    }
}

// Integration tests require the shadow JAR to be built first
tasks.test {
    dependsOn(tasks.shadowJar)
}

// Copy JARs for jpackage input
tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("jpackage-input"))
}

tasks.register<Copy>("copyJar") {
    from(tasks.jar)
    into(layout.buildDirectory.dir("jpackage-input"))
}

// jpackage configuration
tasks.jpackage {
    dependsOn("build", "copyDependencies", "copyJar")

    input.set(layout.buildDirectory.dir("jpackage-input"))
    destination.set(layout.buildDirectory.dir("jpackage"))

    appName = "santa-cli"
    appVersion = project.version.toString()
        .removePrefix("v")
        .removeSuffix("-SNAPSHOT")
        .let { if (it.isEmpty() || it == "unspecified") "1.0.0" else it }
    vendor = "santa-lang"
    appDescription = "Santa-lang CLI - Donner JVM implementation"

    mainJar = tasks.jar.get().archiveFileName.get()
    mainClass = "santa.cli.MainKt"

    type = org.panteleyev.jpackage.ImageType.APP_IMAGE

    javaOptions = listOf("-Dfile.encoding=UTF-8")

    windows {
        winConsole = true
    }
}
