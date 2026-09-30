import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.0"
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "com.lagradost.cloudstream3"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        // Disables some unnecessary features
        freeCompilerArgs.addAll(
            listOf(
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions"
            )
        )

        jvmTarget.set(JvmTarget.JVM_11)  // Required
    }
}

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly(gradleApi())

    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("com.android.tools.build:builder:8.7.3")
    implementation("com.android.tools:sdk-common:31.7.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")

    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
    implementation("com.github.vidstige:jadb:v1.2.1")
}

gradlePlugin {
    plugins {
        create("com.lagradost.cloudstream3.gradle") {
            id = "com.lagradost.cloudstream3.gradle"
            implementationClass = "com.lagradost.cloudstream3.gradle.CloudstreamPlugin"
        }
    }
}

publishing {
    repositories {
        mavenLocal()

        val token = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")

        if (token != null) {
            maven {
                credentials {
                    username = "recloudstream"
                    password = token
                }
                setUrl("https://maven.pkg.github.com/recloudstream/gradle")
            }
        }
    }
}
