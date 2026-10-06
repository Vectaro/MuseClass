import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Адреса бойового сервера не вшита в репо: її вписують у local.properties
// рядком museclass.releaseApiUrl=https://.../api. Без неї release не збирається.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseApiUrl = localProperties.getProperty("museclass.releaseApiUrl")?.trim().orEmpty()

android {
    namespace = "ua.museclass.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ua.museclass.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // емулятор бачить ПК на 10.0.2.2, dev-сервер — порт 8081
            buildConfigField("String", "API_BASE", "\"http://10.0.2.2:8081/api\"")
        }
        release {
            buildConfigField("String", "API_BASE", "\"$releaseApiUrl\"")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time і нові частини java.util на API 24
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":musicxml"))
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.okhttp)
    implementation(libs.gson)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

val checkReleaseApiUrl = tasks.register("checkReleaseApiUrl") {
    val url = releaseApiUrl
    doLast {
        if (url.isEmpty()) {
            throw GradleException(
                "Немає адреси сервера для release: впиши museclass.releaseApiUrl у local.properties"
            )
        }
        if (!url.startsWith("https://")) {
            throw GradleException("museclass.releaseApiUrl має починатися з https://, зараз: $url")
        }
    }
}
tasks.named { it == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseApiUrl) }
