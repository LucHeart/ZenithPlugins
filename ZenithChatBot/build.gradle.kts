plugins {
    id("zenith-plugin")
}

dependencies {
    shade("com.anthropic:anthropic-java:3.8.0") {
        // zenith already has slf4j
        exclude(group = "org.slf4j")
    }

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    // zenith and other plugins bring their own jackson-annotations, okhttp etc., so the SDK gets private copies
    val shadow = "${project.group}.shadow"
    relocate("com.fasterxml", "$shadow.fasterxml")
    relocate("okhttp3", "$shadow.okhttp3")
    relocate("okio", "$shadow.okio")
    relocate("io.opentelemetry", "$shadow.opentelemetry")
    relocate("io.swagger", "$shadow.swagger")
    relocate("com.github.victools", "$shadow.victools")
    relocate("com.standardwebhooks", "$shadow.standardwebhooks")
}
