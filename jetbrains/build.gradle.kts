import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "dev.pacmon"
version = "0.3.0"

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.6.2")
        bundledModule("com.intellij.modules.json")
        testFramework(TestFrameworkType.Platform)
    }
    implementation("org.commonmark:commonmark:0.24.0")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.test {
    useJUnit()
}

tasks.processResources {
    from("../assets/AGENT-RULES.md") {
        into("pacmon")
    }
}
