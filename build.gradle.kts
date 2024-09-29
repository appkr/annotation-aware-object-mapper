plugins {
    kotlin("jvm") version "1.9.10"
    `java-library`
}

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
