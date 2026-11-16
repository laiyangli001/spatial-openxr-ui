plugins { id("com.android.library"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "com.moonlightxr.nativeui"
    compileSdk = 37
    ndkVersion = "30.0.16248370"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    defaultConfig { minSdk = 29; ndk { abiFilters += "arm64-v8a" } }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
}

// Optional original Filament adapter; no product model assets are distributed.
if (providers.gradleProperty("withFilament").orNull == "true") {
    android.sourceSets.getByName("main").kotlin.srcDir("src/filament/java")
    dependencies {
        implementation("com.google.android.filament:filament-android:1.80.0")
        implementation("com.google.android.filament:gltfio-android:1.80.0")
        implementation("com.google.android.filament:filament-utils-android:1.80.0")
    }
}


