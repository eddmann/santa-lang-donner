plugins {
    application
}

application {
    mainClass.set("santa.cli.MainKt")
    applicationName = "santa-cli"
}

dependencies {
    implementation(project(":compiler"))
    implementation(project(":runtime"))
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
}
