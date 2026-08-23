/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.m0e_n00b.viriviri

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MoviePanel : ComponentActivity() {
  override fun onCreate(savedInstanceBundle: Bundle?) {
    super.onCreate(savedInstanceBundle)
    // UX: the original angled left panel remains video Detail in every Workbench route.
    setContent { ImmersiveLeftPanel() }
  }
}
