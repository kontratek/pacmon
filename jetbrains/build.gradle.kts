import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "dev.pacmon"
version = "0.3.1"

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
            // The build target is always verified. Without the property below, so is the
            // newest release of every IDE branch after it, IntelliJ IDEA Community only:
            // one plugin.xml serves every product, and the Marketplace verifies the full
            // matrix after an upload. recommended() would add every EAP branch as well,
            // and each IDE is 1.2 GB to download and 3.5 GB unpacked, so a laptop short
            // of disk runs `./gradlew verifyPlugin -Ppacmon.verify=current`. Raise
            // sinceBuild here together with ideaVersion.sinceBuild above.
            current()
            if (providers.gradleProperty("pacmon.verify").orNull != "current") {
                select {
                    types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
                    channels = listOf(ProductRelease.Channel.RELEASE)
                    sinceBuild = "253"
                }
            }
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
