import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    kotlin("plugin.serialization") version "1.4.10"
    application
}

group = "ru.senin.kotlin.wiki"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("com.apurebase:arkenv:3.1.0")
    implementation("org.apache.commons:commons-compress:1.20")
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
//    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
//    implementation("org.jetbrains.lets-plot:lets-plot-batik:1.5.4")
    api("org.jetbrains.lets-plot:lets-plot-common:2.0.1")
    api("org.jetbrains.lets-plot-kotlin:lets-plot-kotlin-api:1.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.0")
    testImplementation(kotlin("test-junit5"))
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

kotlin.sourceSets.all {
    languageSettings.apply {
        useExperimentalAnnotation("kotlin.time.ExperimentalTime")
    }
}

application {
    mainClass.set("ru.senin.kotlin.wiki.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
