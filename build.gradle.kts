import java.util.Properties

plugins {
    id("java")
    id("application")
}

group = "com.eye"
val releaseProperties = Properties().apply {
    file("src/main/resources/app-version.properties").inputStream().use { load(it) }
}
version = releaseProperties.getProperty("version")

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

application {
    mainClass.set("com.eye.app.Main")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// UI copy is UTF-8 on Windows as well as other build hosts.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// The native EyeRelax.exe locates the bundled runtime; keep the JAR entry point for that launcher.
tasks.jar { manifest { attributes["Main-Class"] = "com.eye.app.Main"; attributes["Class-Path"] = "jna-5.14.0.jar jna-platform-5.14.0.jar" } }

// Portable domain regressions use plain Java and add no test framework dependency.
val verifyPlanCompatibility by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.eye.plan.PlanRefactorAcceptance")
}
val verifyAccounting by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.eye.core.AccountingAcceptance")
    val output = layout.buildDirectory.dir("verification/accounting")
    doFirst { output.get().asFile.mkdirs(); args(output.get().asFile.absolutePath) }
}
val verifyRecorder by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.eye.core.RecorderAcceptance")
    val output = layout.buildDirectory.dir("verification/recorder")
    doFirst { output.get().asFile.mkdirs(); args(output.get().asFile.absolutePath) }
}
tasks.register("verifyCore") {
    group = "verification"
    dependsOn(verifyPlanCompatibility, verifyAccounting, verifyRecorder)
}
tasks.check { dependsOn("verifyCore") }
