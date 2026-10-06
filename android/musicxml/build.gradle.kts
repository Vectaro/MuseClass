// Розбір MusicXML без залежностей від Android: чиста Java, тести на JVM.
// XmlPullParser на пристрої дає сама платформа, у тестах — kxml2.
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
    // українські повідомлення тестів на Windows без кракозябр
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

dependencies {
    compileOnly(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(libs.junit)
    testImplementation(libs.gson)
}
