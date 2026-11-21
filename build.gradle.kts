import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.intellij.platform.grammarkit")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(25)
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        phpstorm(providers.gradleProperty("platformVersion"))
        bundledPlugins("com.jetbrains.php", "com.intellij.css", "org.jetbrains.plugins.less")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            current()
            select {
                types = listOf(IntelliJPlatformType.PhpStorm)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = providers.gradleProperty("pluginSinceBuild")
            }
        }
    }
}

tasks {
    generateLexer {
        sourceFile = file("src/main/grammar/Xf.flex")
    }

    generateParser {
        sourceFile = file("src/main/grammar/Xf.bnf")
    }
}

sourceSets {
    main {
        java {
            srcDir(tasks.generateLexer)
            srcDir(tasks.generateParser)
        }
    }
}
