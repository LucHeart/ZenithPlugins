plugins {
    id("zenith-plugin")
}

val mc = property("mc") as String

// mcprotocollib moves packet classes between MC versions, so code that names one exists per version
sourceSets { main { java.srcDir(if (mc.startsWith("1.")) "src/mc1/java" else "src/mc26/java") } }

dependencies {
    shade("io.prometheus:prometheus-metrics-core:1.10.0")
    shade("io.prometheus:prometheus-metrics-exporter-httpserver:1.10.0")
    shade("io.prometheus:prometheus-metrics-instrumentation-jvm:1.10.0")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    // relocated so another plugin shipping the prometheus client can't clash with this one
    relocate("io.prometheus", "${project.group}.shadow.prometheus")
}
