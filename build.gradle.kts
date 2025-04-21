plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

val mcdevVersion: String by project
val mcdevIdeaVersion: String by project
val coreVersion: String by project

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
        create("IC", mcdevIdeaVersion)
        plugins("com.demonwav.minecraft-dev:$mcdevIdeaVersion-$mcdevVersion")
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