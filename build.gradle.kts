plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.16.1"
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
}

intellij {
    version.set(mcdevIdeaVersion)
    type.set("IC")

    plugins.addAll("java", "maven", "gradle", "Groovy", "ByteCodeViewer", "properties")

    // Mcdev
    plugins.add("com.demonwav.minecraft-dev:$mcdevIdeaVersion-$mcdevVersion")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
