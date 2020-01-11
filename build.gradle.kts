import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
//    kotlin("plugin.spring") version "1.3.50"
}

group = "com.procurement"
version = "1.1.1"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
    jcenter()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.procurement.felicia.infrastructure.web.controllers.SubscriptionControllerKt"
    }
}

dependencies {

    implementation(group = "org.apache.kafka", name = "kafka-clients", version = "2.4.0")
    testCompile(group = "org.jetbrains.kotlin", name = "kotlin-test-junit5", version = "1.3.21")

    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = "2.10.1")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "1.3.61")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.3")

    implementation(group = "io.ktor", name = "ktor-server-core", version = "1.2.6")
    implementation(group = "io.ktor", name = "ktor-server-netty", version = "1.2.6")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Test> {
    setScanForTestClasses(false)
    include("**/*Test.class")  // whatever Ant pattern matches your test class files
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}
