import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// ── kmp-tor friend access ─────────────────────────────────────────────────────
// net/TorConfigCompat.kt has to reach kmp-tor's internal TorSetting factories:
// bridges (UseBridges / Bridge / ClientTransportPlugin) have no public config
// API in kmp-tor 2.6.0 (upstream issue #626) and writing torrc directly is
// unsupported. Kotlin's `internal` is per-module, and -Xfriend-paths is the
// compiler's sanctioned way to widen it — so those calls stay compile-time
// checked instead of becoming reflection that would fail silently at runtime.
// A kmp-tor upgrade that reshapes those factories breaks the build, loudly,
// which is the intent. Delete this once kmp-tor supports bridges natively.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    val compileClasspath = libraries
    compilerOptions.freeCompilerArgs.add(
        providers.provider {
            val jar = compileClasspath.files.firstOrNull { it.name.startsWith("runtime-core-jvm") }
                ?: throw GradleException(
                    "kmp-tor runtime-core-jvm jar not found on the compile classpath — " +
                        "net/TorConfigCompat.kt needs it as a -Xfriend-paths entry."
                )
            "-Xfriend-paths=${jar.absolutePath}"
        }
    )
}

android {
    namespace = "com.haze.mobile"
    compileSdk = 37

    defaultConfig {
        // Play Store identity (permanent once published). Reverse-DNS of the
        // project domain haze.berkkucukk.com.tr. The in-code package/namespace
        // stays com.haze.mobile — applicationId need not match it.
        applicationId = "tr.com.berkkucukk.haze"
        minSdk = 26
        targetSdk = 37   // Android 17; exceeds Play's API 36+ requirement (deadline 2026-08-31)
        versionCode = 22
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Native payload is dominated by Go binaries: kmp-tor's tor plus
            // IPtProxy's lyrebird/snowflake bundle (~24 MB per ABI). Dropping
            // 32-bit x86 — emulator-only, no shipping device uses it — keeps the
            // universal APK from carrying a fourth copy. ABI splits would trim
            // more, but they rename the outputs that build-apk.sh looks for;
            // Play Store installs already download one ABI from the .aab.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Release signing — credentials read from gradle.properties (kept out of git).
    // See the RELEASE section below; falls back to no signing if not configured.
    signingConfigs {
        create("release") {
            val storePath = (project.findProperty("HAZE_KEYSTORE_FILE") as String?)
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = project.findProperty("HAZE_KEYSTORE_PASSWORD") as String?
                keyAlias = project.findProperty("HAZE_KEY_ALIAS") as String?
                keyPassword = project.findProperty("HAZE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign only if a keystore was provided.
            if (project.findProperty("HAZE_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9 removed android.kotlinOptions; JVM target is configured via the
    // Kotlin Gradle plugin's compilerOptions DSL instead.


    buildFeatures {
        compose = true
        buildConfig = true   // needed for BuildConfig.DEBUG (release log suppression)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // kmp-tor resource-exec-tor ships the tor binary as a native library
        // that must be extracted to nativeLibraryDir on install.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle)
    implementation(libs.kmptor.runtime)
    implementation(libs.kmptor.resource.exec)
    implementation(libs.iptproxy)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.ktor.client.cio)
    androidTestImplementation(libs.ktor.client.websockets)
    androidTestImplementation(libs.kotlinx.coroutines.android)
}

// Copy the signed release artifacts to stable names (haze.aab / haze.apk) under
// app/build/outputs/haze/. AGP 9 dropped the old applicationVariants rename API,
// so a copy step wired to the release tasks is the reliable way to fix the names.
val hazeDist = layout.buildDirectory.dir("outputs/haze")

val copyReleaseAab = tasks.register<Copy>("copyReleaseAab") {
    from(layout.buildDirectory.dir("outputs/bundle/release")) { include("*.aab") }
    into(hazeDist)
    rename { "haze.aab" }
}

val copyReleaseApk = tasks.register<Copy>("copyReleaseApk") {
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") }
    into(hazeDist)
    rename { "haze.apk" }
}

// AGP registers these variant tasks lazily, so match by name rather than named().
tasks.matching { it.name == "bundleRelease" }.configureEach { finalizedBy(copyReleaseAab) }
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseApk) }
