package com.viriviri.app.meta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.viriviri.ui.browse.BrowseScreen

class PanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BrowseScreen(
                onEnterImmersive = {
                    HybridTransitionController.launchImmersiveFromPanel(this)
                },
            )
        }
    }
}
