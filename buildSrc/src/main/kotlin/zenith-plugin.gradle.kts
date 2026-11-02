import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// Shared setup of every plugin in this repository: apply with id("zenith-plugin")

plugins {
    id("zenithproxy.plugin.dev")
}

val mc = property("mc") as String
val pluginId = property("plugin_id") as String
val zenithVersion = property("zenith_version") as String
// ZenithProxy for 1.21.x runs on Java 21, 26.x needs Java 25
val javaRelease = if (mc.startsWith("1.")) 21 else 25

group = property("maven_group") as String
// zenith only accepts x.y.z plugin versions, the MC version goes in the jar name instead
version = property("plugin_version") as String

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

zenithProxyPlugin {
    // Generates a BuildConstants.java file to the project's group java package
    buildConstants {
        fields = mapOf(
            "VERSION" to project.version.toString(),
            "MC_VERSION" to mc,
            "PLUGIN_ID" to pluginId,
            "MAVEN_GROUP" to project.group.toString(),
        )
    }
    // the minimum supported java version for users of the plugin
    javaReleaseVersion = JavaLanguageVersion.of(javaRelease)
    runTaskMixinLauncher = true
}

repositories {
    if (zenithVersion == "snapshot") {
        maven("https://maven.2b2t.vc/snapshots") {
            description = "ZenithProxy Prereleases"
        }
    }
    maven("https://maven.2b2t.vc/releases") {
        description = "ZenithProxy Releases"
    }
    maven("https://maven.2b2t.vc/remote") {
        description = "Dependencies used by ZenithProxy"
    }
    mavenCentral()
}

dependencies {
    "zenithProxy"(
        if (zenithVersion == "snapshot") "com.zenith:ZenithProxy:$mc-SNAPSHOT"
        else "com.zenith:ZenithProxy:$zenithVersion+$mc"
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<ShadowJar>("shadowJar") {
    // one jar per MC version, e.g. ZenithVoice-1.0.0+26.3.0.jar
    archiveVersion = "${project.version}+$mc"
}
