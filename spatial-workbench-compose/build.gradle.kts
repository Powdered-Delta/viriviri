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
  // No androidx.core.ktx: no source in this module imports androidx.core.*, and dropping it keeps the
  // module's Android coupling minimal for a future Compose Multiplatform move. Enforced by
  // scripts/verify-component-boundary.ps1, which reports declared-but-unused Android dependencies.
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation("androidx.compose.material:material")
  implementation("androidx.compose.material:material-icons-extended")
  testImplementation(libs.junit)
}
