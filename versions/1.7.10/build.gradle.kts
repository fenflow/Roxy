import java.util.zip.ZipFile

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = "0.2.0-gtnh-alpha"

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    providers.gradleProperty("roxyVoxyJar").orNull?.let { systemProperty("roxy.voxyJar", it) }
    testLogging { events("passed", "skipped", "failed") }
}

val verifyNoBundledVoxy by tasks.registering {
    group = "verification"
    description = "Checks that the compatibility artifact does not bundle Voxy classes, assets, or nested JARs."
    dependsOn(tasks.jar)
    doLast {
        ZipFile(tasks.jar.get().archiveFile.get().asFile).use { zip ->
            val forbidden = zip.entries().asSequence().map { it.name }.filter {
                it.startsWith("me/cortex/voxy/") || it.startsWith("assets/voxy/") || it.endsWith(".jar")
            }.toList()
            check(forbidden.isEmpty()) { "External dependency content found in Roxy artifact: $forbidden" }
        }
    }
}

tasks.check { dependsOn(verifyNoBundledVoxy) }
