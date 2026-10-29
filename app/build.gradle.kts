plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Versioning: the VERSION file holds the release version; CI passes -PversionCode=<github.run_number>
// and -PversionName. Local builds are "<VERSION>-dev".
val fileVersion = providers.fileContents(rootProject.layout.projectDirectory.file("VERSION")).asText.map { it.trim() }
val somnaVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1
val somnaVersionName = providers.gradleProperty("versionName").orNull?.removePrefix("v") ?: "${fileVersion.get()}-dev"

// Signing: provided only through environment variables (CI secrets) — nothing sensitive in the repo.
val keystorePath: String? = System.getenv("SOMNA_KEYSTORE_PATH")
val hasReleaseSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "com.karelisio.somna"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.karelisio.somna"
        minSdk = 26
        targetSdk = 37
        versionCode = somnaVersionCode
        versionName = somnaVersionName
        buildConfigField("String", "UPDATE_REPO", "\"karelisio/somna\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("SOMNA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SOMNA_KEY_ALIAS")
                keyPassword = System.getenv("SOMNA_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Recordings are read through openRawResourceFd, which needs them stored uncompressed.
        noCompress += "ogg"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
            "androidx.compose.animation.ExperimentalAnimationApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Screenshot tests (Robolectric native graphics) only run on demand: ./gradlew testDebugUnitTest -Pscreenshots
tasks.withType<Test>().configureEach {
    // Robolectric needs these on recent JDKs.
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
    maxHeapSize = "3g"
    // Optional Maven mirror for Robolectric's Android jars (-Probolectric.repo=…).
    providers.gradleProperty("robolectric.repo").orNull?.let { systemProperty("robolectric.dependency.repo.url", it) }
    // Optional seam check on the real recordings, decoded to raw PCM (-Ploops.dir=…, see LoopGeneratorTest).
    providers.gradleProperty("loops.dir").orNull?.let { systemProperty("somna.loops.dir", it) }
    val screenshots = providers.gradleProperty("screenshots")
    if (screenshots.isPresent) {
        systemProperty("somna.screenshots.dir", rootProject.layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
        filter { includeTestsMatching("*ScreenshotTest*") }
    } else {
        exclude("**/screenshots/**")
    }
}
