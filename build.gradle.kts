plugins {
    kotlin("jvm") version "2.4.0"
    id("com.diffplug.spotless") version "8.6.0"
    id("com.modrinth.minotaur") version "2.+"
}

group = "one.ruri"
version = "1.3.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

spotless {
    kotlin {
        ktlint()
    }
}

repositories {
    mavenCentral()
    maven {
        name = "spigotmc-repo"
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "codemc-repo"
        url = uri("https://repo.codemc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.70-stable")
    compileOnly("fr.xephi:authme:5.7.0")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    implementation(kotlin("stdlib"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    from({
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to "one.ruri.authmeplus.Main",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("pp17mZ16")
    versionNumber.set(project.version.toString())
    versionType.set("release")
    uploadFile.set(tasks.jar)
    gameVersions.addAll("26.1", "26.1.1", "26.1.2")
    loaders.addAll("paper", "folia")
    changelog.set(System.getenv("CHANGELOG"))
    dependencies {
        optional.project("authmereloaded")
        optional.project("authmerereloaded")
    }
}
