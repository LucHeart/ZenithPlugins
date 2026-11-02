plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://maven.2b2t.vc/releases")
    gradlePluginPortal()
}

dependencies {
    // the plugin marker of id("zenithproxy.plugin.dev"), so the convention plugin can apply it
    implementation("zenithproxy.plugin.dev:zenithproxy.plugin.dev.gradle.plugin:1.2.+")
}
