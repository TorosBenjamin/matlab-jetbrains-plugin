import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.github.kornilova203"
version = "0.9.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Community build: the plugin only needs the shared language APIs, and
        // Ultimate-only startup code breaks the test sandbox.
        intellijIdeaCommunity("2026.2.3") { useInstaller = false }
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

sourceSets {
    main {
        java.srcDirs("src", "gen")
        kotlin.srcDirs("src", "gen")
        resources.srcDirs("resources")
    }
    test {
        java.srcDirs("tests")
        kotlin.srcDirs("tests")
        resources.srcDirs("testData")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}

