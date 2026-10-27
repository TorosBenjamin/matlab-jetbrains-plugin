import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("org.jetbrains.grammarkit") version "2023.3.0.4"
}

group = "io.github.bendzsi"
version = "1.0.0"

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
        // Structure view classes moved from the core platform into this bundled plugin in 2026.x.
        bundledPlugin("intellij.structureView.plugin")
        bundledModule("intellij.spellchecker")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
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
    // Marketplace signing and upload read their secrets from the environment, never from the repository:
    //   CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD (./gradlew signPlugin) and PUBLISH_TOKEN (./gradlew publishPlugin)
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    pluginVerification {
        ides {
            create("IC", "2025.2.4") { useInstaller = false }
            create("IC", "2026.2.3") { useInstaller = false }
        }
    }
}


tasks.test {
    // Optional: folder of MATLAB code for CorpusParsingTest (-PmatlabCorpus=/path).
    systemProperty("matlab.corpus", findProperty("matlabCorpus")?.toString() ?: "")
    systemProperty("matlab.corpus.report", layout.buildDirectory.file("corpus-report.txt").get().asFile.path)
    testLogging { showStandardStreams = findProperty("matlabCorpus") != null }
}

// Regenerate lexers and parser into gen/ (committed, so normal builds don't need this):
//     ./gradlew generateParserCode
grammarKit {
    grammarKitRelease = "2023.3.4"
    jflexRelease = "1.9.2"
}
val generateMatlabLexer = tasks.register<org.jetbrains.grammarkit.tasks.GenerateLexerTask>("generateMatlabLexer") {
    sourceFile.set(file("grammar/MatlabLexer.flex"))
    targetOutputDir.set(file("gen/com/github/kornilova203/matlab/lexer"))
}
val generateSingleQuoteLexer = tasks.register<org.jetbrains.grammarkit.tasks.GenerateLexerTask>("generateSingleQuoteLexer") {
    sourceFile.set(file("grammar/SingleQuoteStringLexer.flex"))
    targetOutputDir.set(file("gen/com/github/kornilova203/matlab/lexer"))
}
val generateMatlabParser = tasks.register<org.jetbrains.grammarkit.tasks.GenerateParserTask>("generateMatlabParser") {
    sourceFile.set(file("grammar/Matlab.bnf"))
    targetRootOutputDir.set(file("gen"))
    pathToParser.set("com/github/kornilova203/matlab/MatlabParser.java")
    pathToPsiRoot.set("com/github/kornilova203/matlab/psi")
}
tasks.register("generateParserCode") {
    group = "grammarkit"
    dependsOn(generateMatlabLexer, generateSingleQuoteLexer, generateMatlabParser)
}
