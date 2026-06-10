plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.viriviri.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }
}

kotlin {
    jvmToolchain(17)
}
