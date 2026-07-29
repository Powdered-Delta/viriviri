package com.viriviri.app.meta

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.viriviri.app.meta.player.PlayerManager
import com.viriviri.app.meta.player.PlaybackLifecycleOwnership
import com.viriviri.app.meta.player.SurfaceHandoffTextureView
import com.viriviri.core.state.HandoffTarget
import com.viriviri.ui.browse.SurfaceHandoffPocScreen
import kotlinx.coroutines.delay

class PanelActivity : ComponentActivity() {
    private lateinit var playerManager: PlayerManager
    private var incomingTransitionId by mutableLongStateOf(NO_TRANSITION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerManager = PlayerManager.getInstance(this)
        playerManager.loadTestMedia()
        incomingTransitionId = intent.transitionId()

        setContent {
            var firstFrameReady by remember(incomingTransitionId) { mutableStateOf(false) }
            val maskAlpha by animateFloatAsState(
                targetValue = if (firstFrameReady) 0f else 1f,
                label = "panel-first-frame-mask",
            )

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
                    val transitionId = incomingTransitionId.takeIf { it >= 0 }
                    val onSurfaceAttached = {
                        HybridTransitionController.onDestinationSurfaceAttached(
                            activity = this,
                            transitionId = transitionId,
                            destination = HandoffTarget.SYSTEM_2D_PANEL,
                        )
                    }
                    val onFirstFrame = {
                        firstFrameReady = true
                        HybridTransitionController.onDestinationFirstFrame(
                            activity = this,
                            transitionId = transitionId,
                            destination = HandoffTarget.SYSTEM_2D_PANEL,
                        )
                    }
                    AndroidView(
                        factory = { context ->
                            SurfaceHandoffTextureView(
                                context = context,
                                playerManager = playerManager,
                                target = HandoffTarget.SYSTEM_2D_PANEL,
                                transitionKey = transitionId,
                                onSurfaceAttached = onSurfaceAttached,
                                onFirstFrame = onFirstFrame,
                            )
                        },
                        update = { view ->
                            view.bindHandoff(transitionId, onSurfaceAttached, onFirstFrame)
                        },
                        modifier = modifier,
                    )
                },
                transitionMask = { modifier ->
                    Box(modifier = modifier.background(Color.Black.copy(alpha = maskAlpha)))
                },
                onReturnToImmersive = {
                    HybridTransitionController.launchImmersiveFromPanel(this)
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingTransitionId = intent.transitionId()
    }

    override fun onStop() {
        releasePlayerIfUnowned()
        super.onStop()
    }

    override fun onDestroy() {
        releasePlayerIfUnowned()
        HybridTransitionController.onActivityDestroyed(this)
        super.onDestroy()
    }

    private fun releasePlayerIfUnowned() {
        if (
            PlaybackLifecycleOwnership.shouldReleasePlayer(
                isChangingConfigurations = isChangingConfigurations,
                isHandoffProtected = HybridTransitionController.isPlayerHandoffProtected(
                    activity = this,
                    incomingTransitionId = incomingTransitionId.takeIf { it >= 0 },
                ),
            )
        ) {
            playerManager.release()
        }
    }

    private fun Intent.transitionId(): Long =
        getLongExtra(HybridTransitionController.TRANSITION_ID_EXTRA, NO_TRANSITION)

    private companion object {
        const val NO_TRANSITION = -1L
    }
}
