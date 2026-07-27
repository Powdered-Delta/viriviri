package com.viriviri.app.meta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import com.viriviri.app.meta.player.PlayerManager
import com.viriviri.app.meta.player.SurfaceHandoffTextureView
import com.viriviri.ui.browse.SurfaceHandoffPocScreen

class PanelActivity : ComponentActivity() {
    private lateinit var playerManager: PlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerManager = PlayerManager.getInstance(this)
        playerManager.loadTestMedia()

        setContent {
            DisposableEffect(Unit) {
                onDispose(playerManager::refreshPlaybackPosition)
            }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1_000)
                    playerManager.refreshPlaybackPosition()
                }
            }
            SurfaceHandoffPocScreen(
                metrics = playerManager.metrics,
                videoTarget = { modifier ->
                    AndroidView(
                        factory = { context ->
                            SurfaceHandoffTextureView(context, playerManager)
                        },
                        modifier = modifier,
                    )
                },
            )
        }
    }

    override fun onDestroy() {
        if (isFinishing) playerManager.release()
        super.onDestroy()
    }
}
