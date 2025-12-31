plugins {
    java
    application
    // Use the maintained fork of the Shadow plugin
    id("com.gradleup.shadow") version "8.3.5"
}

group = "io.github.vegazsk"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
}

application {
    mainClass.set("editor.HighwayEditor")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Configures the JAR generation
tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "editor.HighwayEditor"
    }
}