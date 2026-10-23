plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.kotlin.android)
}
android {
  namespace = "com.m0e_n00b.inputmethod.engine"
  compileSdk = 36
  defaultConfig {
    minSdk = 34
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {
  api(project(":spatial-workbench-core"))
  implementation("org.wolvic.thirdparty:openwnn:1.3.7")
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}
