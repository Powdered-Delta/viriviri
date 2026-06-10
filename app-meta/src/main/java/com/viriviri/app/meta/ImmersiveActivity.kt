package com.viriviri.app.meta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.viriviri.ui.immersive.ImmersiveScreen

class ImmersiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImmersiveScreen(
                onReturnToPanel = {
                    HybridTransitionController.returnToPanelInHome(this)
                },
            )
        }
    }
}
