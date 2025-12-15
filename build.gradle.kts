import java.util.Properties

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("com.github.gmazzo.buildconfig") version "5.7.0"
    application
}

val versionFile = File(project.rootDir, "version.properties")
val versionProps = Properties()
if (versionFile.exists()) {
    versionFile.inputStream().use { versionProps.load(it) }
}

var buildNumber = versionProps.getProperty("buildNumber", "0").toInt()

var baseVersion = "1.5.4"

if (project.hasProperty("rbn")) {
    buildNumber = 0
    println("Build number has been reset to 0.")
} else {
    buildNumber++
}

versionProps.setProperty("buildNumber", buildNumber.toString())
versionFile.outputStream().use {
    versionProps.store(it, "This file stores the auto-incremented build number.")
}


version = "$baseVersion.$buildNumber"
group = "com.magnariuk"

println("Build versions: $version")

repositories {
    mavenCentral()
}

buildConfig {
    buildConfigField("APP_NAME", project.name)
    buildConfigField("APP_VERSION", baseVersion)
    buildConfigField("BUILD_NUMBER", buildNumber)
    buildConfigField("BUILD_TIME", System.currentTimeMillis())
    buildConfigField("GITHUB", uri("https://github.com/MagnarIUK/MServerLauncher"))
    buildConfigField("AUTHOR", "MagnarIUK")
    buildConfigField("Tranlators", "MagnarIUK (uk)")
}

dependencies {
    testImplementation(kotlin("test"))
    val cliktVersion = "5.0.3"
    implementation("com.github.ajalt.clikt:clikt-jvm:$cliktVersion")

    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")

    val ktorVersion = "3.3.1"
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    val slf4jVersion = "2.0.17"
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")


    val okioVersion = "3.16.1"
    implementation("com.squareup.okio:okio:${okioVersion}")

    val kotlinxSerializationVersion = "1.9.0"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}
application {
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
    mainClass.set("com.magnariuk.MainKt")
}

tasks.register<Jar>("fatJar") {
    group = "build"
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.magnariuk.MainKt"
    }

    from(sourceSets.main.get().output)

    doLast {
        val outputDir = layout.buildDirectory.dir("distributions/${project.version}").get().asFile
        outputDir.mkdirs()
        val jarFile = archiveFile.get().asFile
        jarFile.copyTo(File(outputDir, jarFile.name), overwrite = true)
        val copied = File(outputDir, jarFile.name)
        copied.renameTo(File(outputDir, "MServerLauncher.jar"))
        val scriptFile = File(outputDir, "ms")
        val scriptContent = """
        #!/bin/bash
        set -e
        
        SCRIPT_NAME="$(basename "$0")"
        SCRIPT_DIR=$(dirname "$0")
        JAR_FILE="MServerLauncher.jar"
        INSTALL_DIR="${'$'}HOME/.local/bin"
        
        if [[ "$1" == "--install" ]]; then
            echo "Installing to ${'$'}INSTALL_DIR..."
        
            if [[ -f "${'$'}INSTALL_DIR/${'$'}SCRIPT_NAME" ]]; then
                echo "Found, seemingly an old version of the script at ${'$'}INSTALL_DIR/${'$'}SCRIPT_NAME"
                read -r -p "Are you sure you want to remove it? (y/N): " response
                case "${'$'}response" in
                [yY][eE][sS]|[yY])
                    echo "Removing old script..."
                    rm -f "${'$'}INSTALL_DIR/${'$'}SCRIPT_NAME"
                    ;;
                *)
                    echo "Aborting installation."
                    exit 1
                    ;;
                esac
            fi
        
            if [[ -f "${'$'}INSTALL_DIR/${'$'}JAR_FILE" ]]; then
                echo "Removing old JAR..."
                rm -f "${'$'}INSTALL_DIR/${'$'}JAR_FILE"
            fi
            
            echo "Copying new script and JAR..."
            cp "${'$'}SCRIPT_DIR/${'$'}SCRIPT_NAME" "${'$'}INSTALL_DIR/"
            cp "${'$'}SCRIPT_DIR/${'$'}JAR_FILE" "${'$'}INSTALL_DIR/"
        
            chmod +x "${'$'}INSTALL_DIR/${'$'}SCRIPT_NAME"
        
            echo "Installed successfully to: ${'$'}INSTALL_DIR/${'$'}SCRIPT_NAME"
            echo "You can now run it with: ${'$'}SCRIPT_NAME"
            exit 0
        fi
        
        java --enable-native-access=ALL-UNNAMED -jar "${'$'}SCRIPT_DIR/${'$'}JAR_FILE" "$@"
        """.trimIndent()
        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true)
    }

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
}

kotlin {
    jvmToolchain(23)
}