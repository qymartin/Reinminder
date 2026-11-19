import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version = "<feature version from VERSION file>.<CI run number>", e.g. 1.0.3.
// The run number only grows, so it doubles as Android's versionCode.
val featureVersion = rootProject.file("VERSION").readText().trim()
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")?.trim()?.takeIf { it.isNotBlank() }
val signingStore: String? = System.getenv("SIGNING_STORE_FILE")
    ?.takeIf { signingPassword != null && File(it).exists() }

android {
    namespace = "io.github.qymartin.reinminder"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.qymartin.reinminder"
        minSdk = 28
        targetSdk = 36
        versionCode = runNumber
        versionName = "$featureVersion.$runNumber"
    }

    signingConfigs {
        create("release") {
            if (signingStore != null && signingPassword != null) {
                storeFile = file(signingStore)
                storePassword = signingPassword
                keyAlias = "reinminder"
                keyPassword = signingPassword
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingStore != null && signingPassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
