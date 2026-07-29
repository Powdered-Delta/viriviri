package com.viriviri.app.meta

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.viriviri.app.meta.player.PlayerManager
import com.viriviri.core.state.HandoffTarget
import com.viriviri.core.state.SourceFinishDisposition
import com.viriviri.core.state.TransitionPlaybackPolicy
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

object HybridTransitionController {
    private const val TAG = "ViriviriHybridPoC"
    private const val OCULUS_2D_CATEGORY = "com.oculus.intent.category.2D"
    private const val HOME_PANEL_PENDING_INTENT_EXTRA = "extra_launch_in_home_pending_intent"
    private const val HANDOFF_TIMEOUT_MS = 15_000L
    const val TRANSITION_ID_EXTRA = "com.viriviri.app.meta.TRANSITION_ID"

    val stageBPolicy = TransitionPlaybackPolicy.CONTINUE_PLAYBACK

    private val nextTransitionId = AtomicLong(1)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pending: PendingTransition? = null
    private var finishingSource: WeakReference<Activity>? = null
    private var timeoutRunnable: Runnable? = null

    @Synchronized
    fun launchImmersiveFromPanel(panelActivity: Activity): Long? {
        val transition = beginTransition(
            sourceActivity = panelActivity,
            source = HandoffTarget.SYSTEM_2D_PANEL,
            destination = HandoffTarget.IMMERSIVE,
        ) ?: return null

        val immersiveIntent = Intent(panelActivity, ImmersiveActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(TRANSITION_ID_EXTRA, transition.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchOrCancel(transition, panelActivity) {
            panelActivity.startActivity(immersiveIntent)
        }
        return transition.id
    }

    @Synchronized
    fun returnToPanelInHome(immersiveActivity: Activity): Long? {
        val transition = beginTransition(
            sourceActivity = immersiveActivity,
            source = HandoffTarget.IMMERSIVE,
            destination = HandoffTarget.SYSTEM_2D_PANEL,
        ) ?: return null

        val panelIntent = Intent(immersiveActivity, PanelActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(OCULUS_2D_CATEGORY)
            putExtra(TRANSITION_ID_EXTRA, transition.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val panelPendingIntent = PendingIntent.getActivity(
            immersiveActivity,
            transition.id.toInt(),
            panelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(HOME_PANEL_PENDING_INTENT_EXTRA, panelPendingIntent)
        }
        launchOrCancel(transition, immersiveActivity) {
            immersiveActivity.startActivity(homeIntent)
        }
        return transition.id
    }

    @Synchronized
    fun onDestinationSurfaceAttached(
        activity: Activity,
        transitionId: Long?,
        destination: HandoffTarget,
    ) {
        val transition = matchingTransition(transitionId, destination) ?: return
        if (!transition.markSurfaceAttached()) return

        val elapsedMs = transition.elapsedMs()
        PlayerManager.getInstance(activity).recordDestinationSurfaceAttached(transition.id, elapsedMs)
        logMilestone("destination_surface_attached", transition, activity, elapsedMs)
    }

    @Synchronized
    fun onDestinationFirstFrame(
        activity: Activity,
        transitionId: Long?,
        destination: HandoffTarget,
    ) {
        val transition = matchingTransition(transitionId, destination) ?: return
        if (!transition.markFirstFrame()) return

        val elapsedMs = transition.elapsedMs()
        val playerManager = PlayerManager.getInstance(activity)
        playerManager.recordDestinationFirstFrame(transition.id, elapsedMs)
        logMilestone("destination_first_frame", transition, activity, elapsedMs)

        cancelTimeout()
        val sourceActivity = transition.sourceActivity.get()
        finishingSource = WeakReference(sourceActivity)
        val finishDisposition = finishSourceActivity(sourceActivity, activity)
        playerManager.recordSourceFinished(transition.id, transition.elapsedMs(), finishDisposition)
        logMilestone(
            milestone = "source_finish_requested",
            transition = transition,
            activity = sourceActivity,
            elapsedMs = transition.elapsedMs(),
            detail = "finishDisposition=${finishDisposition.name} sourceTask=${sourceActivity?.taskId} " +
                "destinationTask=${activity.taskId}",
        )
        pending = null
    }

    @Synchronized
    fun isPlayerHandoffProtected(activity: Activity, incomingTransitionId: Long?): Boolean =
        pending?.let { transition ->
            transition.sourceActivity.get() === activity || transition.id == incomingTransitionId
        } == true || finishingSource?.get() === activity

    @Synchronized
    fun onActivityDestroyed(activity: Activity) {
        if (finishingSource?.get() === activity) finishingSource = null
    }

    private fun beginTransition(
        sourceActivity: Activity,
        source: HandoffTarget,
        destination: HandoffTarget,
    ): PendingTransition? {
        pending?.let {
            Log.w(TAG, "Ignored duplicate route click pendingTransition=${it.id}")
            return null
        }

        val transition = PendingTransition(
            id = nextTransitionId.getAndIncrement(),
            source = source,
            destination = destination,
            requestedAtMs = SystemClock.elapsedRealtime(),
            sourceActivity = WeakReference(sourceActivity),
        )
        pending = transition
        scheduleTimeout(transition)
        PlayerManager.getInstance(sourceActivity).recordRouteRequested(
            transitionId = transition.id,
            source = source,
            destination = destination,
            policy = stageBPolicy,
        )
        logMilestone("route_requested", transition, sourceActivity, 0)
        return transition
    }

    private fun launchOrCancel(
        transition: PendingTransition,
        activity: Activity,
        launch: () -> Unit,
    ) {
        try {
            launch()
        } catch (failure: RuntimeException) {
            if (pending?.id == transition.id) {
                cancelTimeout()
                pending = null
            }
            Log.e(TAG, "Route launch failed transition=${transition.id} activity=${identity(activity)}", failure)
            throw failure
        }
    }

    private fun scheduleTimeout(transition: PendingTransition) {
        val runnable = Runnable {
            synchronized(this) {
                if (pending?.id != transition.id || !transition.markTimedOut()) return@synchronized

                val elapsedMs = transition.elapsedMs()
                transition.sourceActivity.get()?.let(PlayerManager::getInstance)
                    ?.recordTransitionTimedOut(transition.id, elapsedMs)
                logMilestone(
                    milestone = "transition_timed_out",
                    transition = transition,
                    activity = transition.sourceActivity.get(),
                    elapsedMs = elapsedMs,
                    detail = "source retained; late callbacks are stale",
                )
                pending = null
                timeoutRunnable = null
            }
        }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, HANDOFF_TIMEOUT_MS)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
    }

    private fun finishSourceActivity(source: Activity?, destination: Activity): SourceFinishDisposition {
        if (source == null || source.isDestroyed || source.isFinishing || source === destination) {
            return SourceFinishDisposition.SOURCE_ALREADY_GONE
        }

        return if (source.taskId == destination.taskId) {
            source.finish()
            SourceFinishDisposition.FINISH_ACTIVITY
        } else {
            source.finishAndRemoveTask()
            SourceFinishDisposition.REMOVE_SOURCE_TASK
        }
    }

    private fun matchingTransition(transitionId: Long?, destination: HandoffTarget): PendingTransition? {
        val transition = pending
        if (transitionId == null || transition?.id != transitionId || transition.destination != destination) {
            Log.w(
                TAG,
                "Ignored stale callback transition=$transitionId destination=${destination.name} " +
                    "pending=${transition?.id}",
            )
            return null
        }
        return transition
    }

    private fun logMilestone(
        milestone: String,
        transition: PendingTransition,
        activity: Activity?,
        elapsedMs: Long,
        detail: String = "",
    ) {
        val manager = activity?.let(PlayerManager::getInstance)
        val metrics = manager?.metrics
        Log.i(
            TAG,
            "milestone=$milestone transition=${transition.id} source=${transition.source.name} " +
                "destination=${transition.destination.name} policy=${stageBPolicy.name} elapsed=${elapsedMs}ms " +
                "surfaceReady=${transition.surfaceAttached} firstFrame=${transition.firstFrame} " +
                "timedOut=${transition.timedOut} activity=${activity?.let(::identity)} task=${activity?.taskId} " +
                "position=${manager?.player?.currentPosition ?: metrics?.playbackPositionMs}ms " +
                "prepare=${metrics?.prepareCalls} decoder=${metrics?.videoDecoderInitializations} $detail",
        )
    }

    private fun identity(value: Any): String = Integer.toHexString(System.identityHashCode(value))
}

internal class PendingTransition(
    val id: Long,
    val source: HandoffTarget,
    val destination: HandoffTarget,
    private val requestedAtMs: Long,
    val sourceActivity: WeakReference<Activity>,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
) {
    var surfaceAttached: Boolean = false
        private set
    var firstFrame: Boolean = false
        private set
    var timedOut: Boolean = false
        private set

    fun markSurfaceAttached(): Boolean {
        if (surfaceAttached || timedOut) return false
        surfaceAttached = true
        return true
    }

    fun markFirstFrame(): Boolean {
        if (!surfaceAttached || firstFrame || timedOut) return false
        firstFrame = true
        return true
    }

    fun markTimedOut(): Boolean {
        if (firstFrame || timedOut) return false
        timedOut = true
        return true
    }

    fun elapsedMs(): Long = (clockMs() - requestedAtMs).coerceAtLeast(0)
}
