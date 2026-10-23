/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    maven { url = uri("input-method-engine/vendor-maven"); content { includeGroup("org.wolvic.thirdparty") } }
    mavenCentral()
    google()
  }
}

rootProject.name = "ViriViri"

include(":app")
include(":playback-stage-core")
include(":spatial-workbench-core")
include(":spatial-workbench-compose")

include(":input-method-engine")
