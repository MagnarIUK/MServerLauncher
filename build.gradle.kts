plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "com.magnariuk"
version = "1.4.4.3"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.ajalt.clikt:clikt-jvm:5.0.3")

    val ktorVersion = "3.2.3"
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    val slf4jVersion = "2.0.17"
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")


    val okioVersion = "3.16.0"
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
        /*val scriptFile = File(outputDir, "ms")
        val scriptContent = """
            #!/bin/bash
            SCRIPT_DIR=$(dirname "$0")
            java --enable-native-access=ALL-UNNAMED -jar "${'$'}SCRIPT_DIR/${jarFile.name}" "$@"
        """.trimIndent()
        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true)*/
    }

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
}

kotlin {
    jvmToolchain(23)
}