plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.jetbrains.kotlin.plugin.compose)
}

android {
  namespace = "com.m0e_n00b.spatialworkbench.compose"
  compileSdk = 36

  defaultConfig {
    minSdk = 34
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  buildFeatures { compose = true }
}

dependencies {
  api(project(":spatial-workbench-core"))
  implementation(libs.androidx.core.ktx)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation("androidx.compose.material:material")
  implementation("androidx.compose.material:material-icons-extended")
  testImplementation(libs.junit)
}
