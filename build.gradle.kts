import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
    application
    id("com.github.johnrengelman.shadow") version "5.0.0"
}

application {
    mainClassName = "io.ktor.server.netty.EngineMain"
}

group = "org.qlifyw"
version = "1.4.1"

java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
    jcenter()
}

dependencies {

    implementation(group = "io.ktor", name = "ktor-server-core", version = "1.2.6")
    implementation(group = "io.ktor", name = "ktor-server-test-host", version = "1.3.1")

    implementation(group = "org.apache.kafka", name = "kafka-clients", version = "2.4.0")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "1.3.61")
    
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.3")

    testImplementation(group = "org.testcontainers", name = "kafka", version = "1.12.5")
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test-junit5", version = "1.3.21")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}
