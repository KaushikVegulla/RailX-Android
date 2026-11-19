import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kaushik.railway"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kaushik.railway"
        minSdk = 24
        targetSdk = 35
        versionCode = 28
        versionName = "1.11.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Local demo configuration. BuildConfig values are recoverable from the APK.
        val localPropsFile = rootProject.file("local.properties")
        val localProps = Properties()
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { localProps.load(it) }
        }
        fun prop(key: String, fallback: String) =
            localProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

        buildConfigField("String", "SUPABASE_URL", "\"${prop("supabase.url", "https://etaaufimazvjdmpladom.supabase.co")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${prop("supabase.publishable.key", "sb_publishable_XmViLB5DeAomuqBFzv4R8A_UFQNOZYY")}\"")
        buildConfigField("String", "RAILRADAR_API_KEY", "\"${prop("railradar.api.key", System.getenv("RAILRADAR_API_KEY")?.trim()?.takeIf { it.isNotEmpty() } ?: "YOUR_RAILRADAR_API_KEY")}\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"${prop("backend.base.url", "http://10.0.2.2:8088")}\"")
        val useLocalKeys = prop("use.local.keys", "true").lowercase() in listOf("true", "1", "yes")
        buildConfigField("boolean", "USE_LOCAL_KEYS", if (useLocalKeys) "true" else "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.razorpay.checkout)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
