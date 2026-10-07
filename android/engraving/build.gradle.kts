// Верстка нотного стану без Android: партія з :musicxml → список фігур
// (лінії, еліпси, контури, текст) у координатах prototype/engine.js.
// Малює їх застосунок на Canvas; тут — тільки геометрія, тести на JVM.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    // лише API Java 8 — те, що є на Android 7 (API 24)
    options.release.set(8)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options")
}

tasks.test {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

dependencies {
    api(project(":musicxml"))
    testImplementation(libs.kxml2)
    testImplementation(libs.xmlpull)
    testImplementation(libs.junit)
    testImplementation(libs.gson)
}
