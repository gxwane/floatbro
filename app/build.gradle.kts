plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.license)
}

android {
    namespace = "io.github.floatbro"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.floatbro"
        minSdk = 26
        targetSdk = 36
        versionCode = 10006
        versionName = "0.1.5-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug") // fallback
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        ignoreTestSources = true
        baseline = file("lint-baseline.xml")
    }
}

composeCompiler {
    if (project.findProperty("floatbro.enableComposeMetrics") == "true") {
        metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
    }
}

licenseReport {
    generateCsvReport = false
    generateHtmlReport = true
    generateJsonReport = true
    copyHtmlReportToAssets = false
    copyJsonReportToAssets = false
}

dependencies {
    implementation(project(":core:webview"))
    implementation(project(":core:common"))
    implementation(project(":ad-filter"))

    // AndroidX Core & Navigation
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Lifecycle & Service (FloatBrowserService 核心)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.service)

    // DataStore (窗口大小、分屏比例偏好)
    implementation(libs.androidx.datastore.preferences)

    // Jetpack Compose & Material 3
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Koin Dependency Injection
    implementation(libs.koin.android)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Logging & Utility
    implementation(libs.timber)
    implementation(libs.reorderable)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.archunit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)

    // Compose Static Quality Gates (Phase 3)
    lintChecks(libs.slack.compose.lint)
}
