plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0-RC2"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

val mcdevVersion: String by project
val mcdevIdeaVersion: String by project
val coreVersion: String by project
val useLocalMcdev: String by project
val mcdevLocalPath: String by project

group = "com.bawnorton.msp"
version = "$coreVersion+$mcdevIdeaVersion-$mcdevVersion"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
kotlin {
    jvmToolchain {
        languageVersion.set(java.toolchain.languageVersion.get())
    }
}

repositories {
    maven("https://repo.denwav.dev/repository/maven-public/")
    maven("https://maven.fabricmc.net/") {
        content {
            includeModule("net.fabricmc", "mapping-io")
        }
    }
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}


intellijPlatform {
    pluginConfiguration {
        name = "MixinSquared"
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(mcdevIdeaVersion) {
            useInstaller = false
        }
        if (useLocalMcdev.toBoolean()) {
            val libsDir = file("$mcdevLocalPath/build/distributions")
            val localJar = libsDir.listFiles()
                ?.filter { it.name.endsWith("-local.zip") }
                ?.maxByOrNull { it.lastModified() }
                ?: error("No local MCDev build found in ${libsDir.absolutePath} — run ./gradlew buildPlugin in the MCDev project first")
            localPlugin(localJar)
        } else {
            plugins("com.demonwav.minecraft-dev:$mcdevIdeaVersion-$mcdevVersion")
        }
        bundledPlugins(
            "com.intellij.java",
            "org.jetbrains.idea.maven",
            "com.intellij.gradle",
            "org.intellij.groovy",
            "com.intellij.properties",
            "ByteCodeViewer"
        )
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}