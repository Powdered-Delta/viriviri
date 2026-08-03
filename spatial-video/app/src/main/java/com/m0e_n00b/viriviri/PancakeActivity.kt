package com.m0e_n00b.viriviri

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class PancakeActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setTheme(R.style.PanelAppThemeTransparent)
    setContentView(R.layout.window_2d)
    findViewById<android.widget.Button>(R.id.open_immersive_button).setOnClickListener {
      startActivity(
          Intent(this, SpatialVideoSampleActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
      )
    }
  }
}
