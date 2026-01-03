pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.0"
        id("org.panteleyev.jpackageplugin") version "1.7.6"
        id("com.gradleup.shadow") version "9.0.0"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "santa-lang-donner"
include("compiler", "runtime", "cli")
