package com.viriviri.app.meta

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.viriviri.app.meta.player.PlayerManager
import com.viriviri.app.meta.player.SurfaceHandoffTextureView
import com.viriviri.core.state.HandoffTarget
import com.viriviri.core.state.HandoffExperimentMode
import com.viriviri.ui.browse.SurfaceHandoffPocScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PanelActivity : ComponentActivity() {
    private lateinit var playerManager: PlayerManager
    private var incomingTransitionId by mutableLongStateOf(NO_TRANSITION)
    private var sourceDestroyedForRoute by mutableStateOf(false)
    private var routeFailureReason by mutableStateOf<String?>(null)
    private var outgoingTransitionRequested by mutableStateOf(false)
    private var positionRefreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HybridTransitionController.registerAppActivity(this, isVrHost = false)
        playerManager = PlayerManager.getInstance(this)
        incomingTransitionId = intent.transitionId()
        intent.getStringExtra(HybridTransitionController.EXPERIMENT_MODE_EXTRA)?.let { name ->
            runCatching { playerManager.setExperimentMode(HandoffExperimentMode.valueOf(name)) }
        }
        if (incomingTransitionId < 0) playerManager.loadTestMedia(playWhenReady = false)
        sourceDestroyedForRoute = incomingTransitionId < 0 || HybridTransitionController.canAttachDestination(
            incomingTransitionId.takeIf { it >= 0 },
            HandoffTarget.SYSTEM_2D_PANEL,
        )
        Log.i(TAG, "onCreate transition=${incomingTransitionId.takeIf { it >= 0 }} task=$taskId")
        HybridTransitionController.onPanelActivityCreated(this, incomingTransitionId.takeIf { it >= 0 })

        setContent {
            var firstFrameReady by remember(incomingTransitionId) { mutableStateOf(false) }
            val maskAlpha by animateFloatAsState(
                targetValue = if (firstFrameReady) 0f else 1f,
                label = "panel-first-frame-mask",
            )

            DisposableEffect(Unit) {
                onDispose(playerManager::refreshPlaybackPosition)
            }
            SurfaceHandoffPocScreen(
                metrics = playerManager.metrics,
                videoTarget = { modifier ->
                    if (sourceDestroyedForRoute) {
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
                    }
                },
                transitionMask = { modifier ->
                    Box(modifier = modifier.background(Color.Black.copy(alpha = maskAlpha)))
                },
                onReturnToImmersive = {
                    if (!outgoingTransitionRequested) {
                        outgoingTransitionRequested = HybridTransitionController.launchImmersiveFromPanel(this) != null
                    }
                },
                returnToImmersiveEnabled = !outgoingTransitionRequested && (firstFrameReady || routeFailureReason != null),
                returnActionLabel = when {
                    outgoingTransitionRequested -> "Transition in progress"
                    routeFailureReason != null -> "Retry Immersive Mode"
                    firstFrameReady -> "Return to Immersive Mode"
                    else -> "Waiting for source shutdown and video"
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingTransitionId = intent.transitionId()
        Log.i(TAG, "onNewIntent transition=${incomingTransitionId.takeIf { it >= 0 }} task=$taskId")
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart transition=${incomingTransitionId.takeIf { it >= 0 }} task=$taskId")
        positionRefreshJob?.cancel()
        positionRefreshJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    playerManager.refreshPlaybackPosition()
                    kotlinx.coroutines.delay(1_000)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume transition=${incomingTransitionId.takeIf { it >= 0 }} task=$taskId")
    }

    override fun onStop() {
        Log.i(TAG, "onStop transition=${incomingTransitionId.takeIf { it >= 0 }} task=$taskId")
        positionRefreshJob?.cancel()
        positionRefreshJob = null
        releasePlayerIfUnowned()
        super.onStop()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy transition=${incomingTransitionId.takeIf { it >= 0 }} task=$taskId")
        HybridTransitionController.onActivityDestroying(this, incomingTransitionId.takeIf { it >= 0 })
        super.onDestroy()
        HybridTransitionController.onActivityDestroyed(this)
        releasePlayerIfUnowned()
    }

    fun onSourceDestroyedForRoute(transitionId: Long) {
        if (incomingTransitionId != transitionId) return
        playerManager = PlayerManager.getInstance(this)
        playerManager.loadTestMedia(playWhenReady = false)
        HybridTransitionController.restorePlaybackForDestination(this, transitionId, HandoffTarget.SYSTEM_2D_PANEL)
        Log.i(TAG, "source_destroyed_attach_enabled transition=$transitionId task=$taskId")
        sourceDestroyedForRoute = true
    }

    fun onRouteFailed(transitionId: Long, reason: String) {
        if (incomingTransitionId != transitionId) return
        Log.e(TAG, "route_failed transition=$transitionId reason=$reason task=$taskId")
        routeFailureReason = reason
        outgoingTransitionRequested = false
    }

    private fun releasePlayerIfUnowned() {
        if (HybridTransitionController.shouldReleasePlayer(this, isChangingConfigurations)) {
            playerManager.release()
        }
    }

    private fun Intent.transitionId(): Long =
        getLongExtra(HybridTransitionController.TRANSITION_ID_EXTRA, NO_TRANSITION)

    private companion object {
        const val TAG = "ViriviriHybridPoC"
        const val NO_TRANSITION = -1L
    }
}
