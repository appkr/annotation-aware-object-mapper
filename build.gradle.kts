plugins {
    kotlin("jvm") version "1.9.10"
    `java-library`
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-framework-datatest:5.9.1")
    testImplementation("io.mockk:mockk:1.13.12")
}

java {
    sourceCompatibility = JavaVersion.toVersion("8")
    targetCompatibility = JavaVersion.toVersion("8")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

ktlint {
    version.set("1.3.1")
}
