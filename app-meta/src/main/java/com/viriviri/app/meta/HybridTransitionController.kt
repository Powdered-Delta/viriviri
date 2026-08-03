package com.viriviri.app.meta

import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.viriviri.app.meta.player.PlayerManager
import com.viriviri.app.meta.player.PlaybackSnapshot
import com.viriviri.core.state.HandoffRouteState
import com.viriviri.core.state.HandoffTarget
import com.viriviri.core.state.HandoffExperimentMode
import com.viriviri.core.state.SourceFinishDisposition
import com.viriviri.core.state.TransitionPlaybackPolicy
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong

object HybridTransitionController {
    private const val TAG = "ViriviriHybridPoC"
    private const val OCULUS_2D_CATEGORY = "com.oculus.intent.category.2D"
    private const val HOME_PANEL_PENDING_INTENT_EXTRA = "extra_launch_in_home_pending_intent"
    private const val HANDOFF_TIMEOUT_MS = 15_000L
    private const val VR_CLEAR_RETRY_MS = 50L
    private const val VR_CLEAR_RETRY_LIMIT = 40
    const val TRANSITION_ID_EXTRA = "com.viriviri.app.meta.TRANSITION_ID"
    const val EXPERIMENT_MODE_EXTRA = "com.viriviri.app.meta.EXPERIMENT_MODE"

    val stageBPolicy = TransitionPlaybackPolicy.CONTINUE_PLAYBACK

    private val nextTransitionId = AtomicLong(1)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pending: CoordinatedTransition? = null
    private var activeImmersiveActivity: WeakReference<ImmersiveActivity>? = null
    private val activityOwnership = AppActivityOwnershipRegistry<Activity>()
    private var timeoutRunnable: Runnable? = null
    private var immersiveLaunchRunnable: Runnable? = null
    private var selectedExperimentMode = HandoffExperimentMode.DIRECT_RECOVERY

    @Synchronized
    fun setExperimentMode(activity: Activity, mode: HandoffExperimentMode) {
        if (pending != null) return
        selectedExperimentMode = mode
        PlayerManager.getInstance(activity).setExperimentMode(mode)
        Log.i(TAG, "selected_handoff_experiment_mode=${mode.name}")
    }

    fun selectedExperimentMode(): HandoffExperimentMode = selectedExperimentMode

    @Synchronized
    fun registerAppActivity(activity: Activity, isVrHost: Boolean) {
        activityOwnership.register(activity, isVrHost)
        val snapshot = activityOwnership.snapshot()
        Log.i(
            TAG,
            "activity_owner_registered activity=${identity(activity)} type=${activity.javaClass.simpleName} " +
                "vrHost=$isVrHost liveAppActivities=${snapshot.liveAppActivities} liveVrHosts=${snapshot.liveVrHosts}",
        )
    }

    @Synchronized
    fun registerImmersiveActivity(activity: ImmersiveActivity, transitionId: Long?) {
        val existing = activeImmersiveActivity?.get()
        if (existing != null && existing !== activity && !existing.isDestroyed) {
            Log.e(
                TAG,
                "multiple_vr_activity_detected existing=${identity(existing)} incoming=${identity(activity)} " +
                    "transition=$transitionId",
            )
            activity.finish()
            Log.i(
                TAG,
                "duplicate_vr_finish_requested transition=$transitionId activity=${identity(activity)} " +
                    "finishMode=${RouteFinishMode.ACTIVITY_ONLY.name} task=${activity.taskId}",
            )
            failPending("second_vr_activity_created")
            return
        }
        activeImmersiveActivity = WeakReference(activity)
        pending?.takeIf { it.state.id == transitionId }?.let { transition ->
            transition.destinationActivity = WeakReference(activity)
            transition.state.markDestinationCreated()
            transition.playbackSnapshot?.let { snapshot ->
                PlayerManager.getInstance(activity).restorePlaybackSnapshot(snapshot, transition.state.id, HandoffTarget.IMMERSIVE)
            }
        }
    }

    @Synchronized
    fun launchImmersiveFromPanel(panelActivity: PanelActivity): Long? {
        val transition = beginTransition(
            sourceActivity = panelActivity,
            source = HandoffTarget.SYSTEM_2D_PANEL,
            destination = HandoffTarget.IMMERSIVE,
            destinationLaunchBeforeSourceDestroy = false,
        ) ?: return null

        requestSourceFinish(transition, panelActivity)
        return transition.state.id
    }

    @Synchronized
    fun returnToPanelInHome(immersiveActivity: Activity): Long? {
        val transition = beginTransition(
            sourceActivity = immersiveActivity,
            source = HandoffTarget.IMMERSIVE,
            destination = HandoffTarget.SYSTEM_2D_PANEL,
            destinationLaunchBeforeSourceDestroy = true,
        ) ?: return null

        val panelIntent = Intent(immersiveActivity, PanelActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(OCULUS_2D_CATEGORY)
            data = Uri.parse("viriviri://panel/transition/${transition.state.id}")
            putExtra(TRANSITION_ID_EXTRA, transition.state.id)
            putExtra(EXPERIMENT_MODE_EXTRA, selectedExperimentMode.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val panelPendingIntent = PendingIntent.getActivity(
            immersiveActivity,
            transition.state.id.toInt(),
            panelIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(HOME_PANEL_PENDING_INTENT_EXTRA, panelPendingIntent)
        }

        try {
            immersiveActivity.startActivity(homeIntent)
            transition.state.markDestinationLaunched()
            recordRouteState(transition, immersiveActivity)
            Log.i(
                TAG,
                    "route_intent_home_pending_intent transition=${transition.state.id} " +
                    "source=${identity(immersiveActivity)} mode=$selectedExperimentMode " +
                    "panelFlags=${panelIntent.flags} homeFlags=${homeIntent.flags}",
            )
            Log.i(
                TAG,
                "route_waiting_for_panel_activity_created transition=${transition.state.id} " +
                    "source=${identity(immersiveActivity)}",
            )
        } catch (failure: RuntimeException) {
            failTransition(transition, "home_pending_intent_launch_failed", failure)
            throw failure
        }
        return transition.state.id
    }

    @Synchronized
    fun onPanelActivityCreated(activity: PanelActivity, transitionId: Long?) {
        Log.i(
            TAG,
            "route_intent_panel_activity_created transition=$transitionId activity=${identity(activity)} task=${activity.taskId}",
        )
        val transition = pending?.takeIf { it.state.id == transitionId } ?: return
        transition.destinationActivity = WeakReference(activity)
        if (transition.state.markDestinationCreated()) {
            logMilestone("destination_activity_created", transition, activity, transition.state.elapsedMs())
        }
        transition.sourceActivity.get()?.let { source -> requestSourceFinish(transition, source) }
        if (transition.state.canAttachDestination) activity.onSourceDestroyedForRoute(transition.state.id)
    }

    @Synchronized
    fun restorePlaybackForDestination(activity: Activity, transitionId: Long, destination: HandoffTarget) {
        val transition = matchingTransition(transitionId, destination) ?: return
        transition.playbackSnapshot?.let { snapshot ->
            PlayerManager.getInstance(activity).restorePlaybackSnapshot(snapshot, transitionId, destination)
        }
    }

    @Synchronized
    fun canAttachDestination(transitionId: Long?, destination: HandoffTarget): Boolean =
        pending?.let { it.state.id == transitionId && it.state.destination == destination && it.state.canAttachDestination } == true

    @Synchronized
    fun isTransitionPending(transitionId: Long?): Boolean = pending?.state?.id == transitionId

    @Synchronized
    fun transitionIdFor(activity: Activity, fallback: Long? = null): Long? =
        pending?.takeIf { it.sourceActivity.get() === activity || it.destinationActivity?.get() === activity }
            ?.state?.id
            ?: fallback

    @Synchronized
    fun onDestinationSurfaceAttached(
        activity: Activity,
        transitionId: Long?,
        destination: HandoffTarget,
    ) {
        val transition = matchingTransition(transitionId, destination) ?: return
        if (!transition.state.markSurfaceAttached()) return

        val elapsedMs = transition.state.elapsedMs()
        val manager = PlayerManager.getInstance(activity)
        manager.recordDestinationSurfaceAttached(transition.state.id, elapsedMs)
        manager.recordRouteState(transition.state.id, transition.state.phase.toMetricState())
        logMilestone("destination_surface_attached", transition, activity, elapsedMs)
    }

    @Synchronized
    fun onDestinationFirstFrame(
        activity: Activity,
        transitionId: Long?,
        destination: HandoffTarget,
    ) {
        val transition = matchingTransition(transitionId, destination) ?: return
        if (!transition.state.markFirstFrame()) return

        val elapsedMs = transition.state.elapsedMs()
        val playerManager = PlayerManager.getInstance(activity)
        playerManager.recordDestinationFirstFrame(transition.state.id, elapsedMs)
        playerManager.recordRouteState(transition.state.id, HandoffRouteState.COMPLETED)
        logMilestone("destination_first_frame", transition, activity, elapsedMs)
        cancelTimeout()
        pending = null
        transition.clearReferences()
    }

    @Synchronized
    fun isPlayerHandoffProtected(activity: Activity, incomingTransitionId: Long?): Boolean =
        pending?.let { transition ->
            transition.sourceActivity.get() === activity ||
                transition.destinationActivity?.get() === activity ||
                transition.state.id == incomingTransitionId ||
                activeImmersiveActivity?.get() === activity
        } == true

    @Synchronized
    fun shouldReleasePlayer(activity: Activity, isChangingConfigurations: Boolean): Boolean {
        if (isChangingConfigurations || pending != null || isPlayerHandoffProtected(activity, null)) return false
        return PlayerReleaseEligibility.shouldRelease(releaseEligibilitySnapshot())
    }

    @Synchronized
    fun onActivityDestroying(activity: Activity, incomingTransitionId: Long?) {
        val transition = pending ?: return
        val isSource = transition.sourceActivity.get() === activity
        val isDestination = transition.destinationActivity?.get() === activity || transition.state.id == incomingTransitionId
        if (!isSource && !isDestination) return

        if (isDestination && !isSource) {
            failTransition(transition, "destination_destroyed_before_first_frame")
        }
    }

    @Synchronized
    fun onActivityDestroyed(activity: Activity) {
        if (activeImmersiveActivity?.get() === activity) activeImmersiveActivity = null

        activityOwnership.unregister(activity)
        val ownership = activityOwnership.snapshot()
        Log.i(
            TAG,
            "activity_owner_unregistered activity=${identity(activity)} type=${activity.javaClass.simpleName} " +
                "liveAppActivities=${ownership.liveAppActivities} liveVrHosts=${ownership.liveVrHosts}",
        )

        val transition = pending ?: return
        if (transition.sourceActivity.get() !== activity) return

        transition.sourceActivity.clear()
        if (!transition.state.markSourceDestroyed()) return

        val elapsedMs = transition.state.elapsedMs()
        val manager = PlayerManager.getInstance(activity)
        manager.recordSourceDestroyed(transition.state.id, elapsedMs)
        manager.recordRouteState(transition.state.id, transition.state.phase.toMetricState())
        logMilestone("source_activity_destroyed", transition, activity, elapsedMs)
        manager.release()

        when (transition.state.destination) {
            HandoffTarget.SYSTEM_2D_PANEL -> {
                (transition.destinationActivity?.get() as? PanelActivity)
                    ?.onSourceDestroyedForRoute(transition.state.id)
            }
            HandoffTarget.IMMERSIVE -> scheduleImmersiveLaunch(transition)
        }
    }

    private fun beginTransition(
        sourceActivity: Activity,
        source: HandoffTarget,
        destination: HandoffTarget,
        destinationLaunchBeforeSourceDestroy: Boolean,
    ): CoordinatedTransition? {
        pending?.let {
            Log.w(TAG, "Ignored duplicate route click pendingTransition=${it.state.id}")
            return null
        }

        val manager = PlayerManager.getInstance(sourceActivity)
        val snapshot = manager.capturePlaybackSnapshot()
        manager.pauseForHandoff(nextTransitionId.get())
        val transition = CoordinatedTransition(
            state = HybridRouteTransition(
                id = nextTransitionId.getAndIncrement(),
                source = source,
                destination = destination,
                destinationLaunchBeforeSourceDestroy = destinationLaunchBeforeSourceDestroy,
                requestedAtMs = SystemClock.elapsedRealtime(),
            ),
            sourceActivity = WeakReference(sourceActivity),
            applicationContext = sourceActivity.applicationContext,
            playbackSnapshot = snapshot,
        )
        pending = transition
        PlayerManager.getInstance(sourceActivity).setExperimentMode(selectedExperimentMode)
        scheduleTimeout(transition)
        PlayerManager.getInstance(sourceActivity).recordRouteRequested(
            transitionId = transition.state.id,
            source = source,
            destination = destination,
            policy = stageBPolicy,
        )
        logMilestone("route_requested", transition, sourceActivity, 0)
        return transition
    }

    private fun requestSourceFinish(transition: CoordinatedTransition, source: Activity) {
        if (!transition.state.canRequestSourceFinish) {
            Log.i(
                TAG,
                "source_finish_deferred transition=${transition.state.id} destination=${transition.state.destination.name} " +
                    "destinationCreated=${transition.state.destinationCreated}",
            )
            return
        }
        if (!transition.state.markSourceFinishRequested()) return
        source.finish()
        val finishMode = RouteFinishMode.ACTIVITY_ONLY
        val disposition = SourceFinishDisposition.FINISH_ACTIVITY
        PlayerManager.getInstance(source).recordSourceFinished(
            transition.state.id,
            transition.state.elapsedMs(),
            disposition,
        )
        logMilestone(
            "source_finish_requested",
            transition,
            source,
            transition.state.elapsedMs(),
            "finishMode=${finishMode.name} finishDisposition=${disposition.name} sourceTask=${source.taskId}",
        )
    }

    private fun scheduleImmersiveLaunch(transition: CoordinatedTransition, attempt: Int = 0) {
        immersiveLaunchRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            synchronized(this) {
                if (pending !== transition || transition.state.phase != HybridRoutePhase.READY_TO_LAUNCH_DESTINATION) {
                    return@synchronized
                }

                val existing = activeImmersiveActivity?.get()
                if (existing != null && !existing.isDestroyed) {
                    if (attempt >= VR_CLEAR_RETRY_LIMIT) {
                        failTransition(transition, "existing_vr_activity_did_not_destroy")
                    } else {
                        scheduleImmersiveLaunch(transition, attempt + 1)
                    }
                    return@synchronized
                }

                val intent = Intent(transition.applicationContext, ImmersiveActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    putExtra(TRANSITION_ID_EXTRA, transition.state.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    transition.applicationContext.startActivity(intent)
                    transition.state.markDestinationLaunched()
                    recordRouteState(transition, null)
                    Log.i(
                        TAG,
                        "route_intent_new_immersive_after_panel_destroy transition=${transition.state.id} " +
                            "attempt=$attempt flags=${intent.flags}",
                    )
                } catch (failure: RuntimeException) {
                    failTransition(transition, "immersive_launch_failed", failure)
                }
            }
        }
        immersiveLaunchRunnable = runnable
        if (attempt == 0) mainHandler.post(runnable) else mainHandler.postDelayed(runnable, VR_CLEAR_RETRY_MS)
    }

    private fun scheduleTimeout(transition: CoordinatedTransition) {
        val runnable = Runnable {
            synchronized(this) {
                if (pending !== transition) return@synchronized
                val reason = when {
                    !transition.state.destinationCreated ->
                        "transition_timeout:destination_activity_not_materialized"
                    !transition.state.surfaceAttached ->
                        "transition_timeout:destination_surface_not_available"
                    else ->
                        "transition_timeout:destination_first_frame_missing"
                }
                failTransition(transition, reason)
            }
        }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, HANDOFF_TIMEOUT_MS)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        immersiveLaunchRunnable?.let(mainHandler::removeCallbacks)
        immersiveLaunchRunnable = null
    }

    private fun failPending(reason: String) {
        pending?.let { failTransition(it, reason) }
    }

    private fun failTransition(
        transition: CoordinatedTransition,
        reason: String,
        failure: RuntimeException? = null,
    ) {
        if (pending !== transition || !transition.state.fail(reason)) return
        val activity = transition.destinationActivity?.get() ?: transition.sourceActivity.get()
        activity?.let(PlayerManager::getInstance)?.apply {
            if (reason.startsWith("transition_timeout")) {
                recordTransitionTimedOut(transition.state.id, transition.state.elapsedMs())
            }
            recordTransitionFailed(transition.state.id, reason)
        }
        logMilestone("transition_failed", transition, activity, transition.state.elapsedMs(), "reason=$reason")
        if (failure != null) Log.e(TAG, "Route failed transition=${transition.state.id} reason=$reason", failure)
        (transition.destinationActivity?.get() as? PanelActivity)?.onRouteFailed(transition.state.id, reason)
        cancelTimeout()
        pending = null
        restoreSourceTaskAfterRouteFailure(transition)
        transition.clearReferences()
        val ownership = releaseEligibilitySnapshot()
        Log.i(
            TAG,
            "transition_failure_release_check transition=${transition.state.id} reason=$reason " +
                "liveAppActivities=${ownership.liveAppActivities} liveVrHosts=${ownership.liveVrHosts} " +
                "eligible=${PlayerReleaseEligibility.shouldRelease(ownership)}",
        )
        if (PlayerReleaseEligibility.shouldRelease(ownership)) {
            PlayerManager.releaseInstanceIfPresent()
        }
    }

    private fun restoreSourceTaskAfterRouteFailure(transition: CoordinatedTransition) {
        val source = transition.sourceActivity.get() ?: return
        try {
            source.getSystemService(ActivityManager::class.java)
                ?.moveTaskToFront(source.taskId, 0)
            Log.i(
                TAG,
                "route_source_task_restored_after_failure transition=${transition.state.id} " +
                    "task=${source.taskId} activity=${identity(source)}",
            )
        } catch (failure: RuntimeException) {
            Log.e(
                TAG,
                "route_source_task_restore_failed transition=${transition.state.id} task=${source.taskId}",
                failure,
            )
        }
    }

    private fun releaseEligibilitySnapshot(): AppActivityOwnershipSnapshot {
        val registered = activityOwnership.snapshot()
        val liveVrHost = activeImmersiveActivity?.get()?.let { !it.isDestroyed } == true
        return registered.copy(liveVrHosts = maxOf(registered.liveVrHosts, if (liveVrHost) 1 else 0))
    }

    private fun matchingTransition(transitionId: Long?, destination: HandoffTarget): CoordinatedTransition? {
        val transition = pending
        if (transitionId == null || transition?.state?.id != transitionId || transition.state.destination != destination) {
            Log.w(
                TAG,
                "Ignored stale callback transition=$transitionId destination=${destination.name} " +
                    "pending=${transition?.state?.id}",
            )
            return null
        }
        return transition
    }

    private fun recordRouteState(transition: CoordinatedTransition, activity: Activity?) {
        val host = activity ?: transition.destinationActivity?.get() ?: transition.sourceActivity.get() ?: return
        PlayerManager.getInstance(host).recordRouteState(
            transition.state.id,
            transition.state.phase.toMetricState(),
        )
    }

    private fun logMilestone(
        milestone: String,
        transition: CoordinatedTransition,
        activity: Activity?,
        elapsedMs: Long,
        detail: String = "",
    ) {
        val manager = activity?.let(PlayerManager::getInstance)
        val metrics = manager?.metrics
        Log.i(
            TAG,
            "milestone=$milestone transition=${transition.state.id} source=${transition.state.source.name} " +
                "destination=${transition.state.destination.name} phase=${transition.state.phase.name} " +
                "policy=${stageBPolicy.name} elapsed=${elapsedMs}ms " +
                "sourceDestroyed=${transition.state.sourceDestroyed} surfaceReady=${transition.state.surfaceAttached} " +
                "firstFrame=${transition.state.firstFrame} activity=${activity?.let(::identity)} task=${activity?.taskId} " +
                "position=${manager?.player?.currentPosition ?: metrics?.playbackPositionMs}ms " +
                "prepare=${metrics?.prepareCalls} decoder=${metrics?.videoDecoderInitializations} $detail",
        )
    }

    private fun HybridRoutePhase.toMetricState(): HandoffRouteState = when (this) {
        HybridRoutePhase.WAITING_FOR_SOURCE_DESTROY -> HandoffRouteState.WAITING_FOR_SOURCE_DESTROY
        HybridRoutePhase.READY_TO_LAUNCH_DESTINATION -> HandoffRouteState.READY_TO_LAUNCH_DESTINATION
        HybridRoutePhase.WAITING_FOR_DESTINATION_SURFACE -> HandoffRouteState.WAITING_FOR_DESTINATION_SURFACE
        HybridRoutePhase.WAITING_FOR_FIRST_FRAME -> HandoffRouteState.WAITING_FOR_FIRST_FRAME
        HybridRoutePhase.COMPLETED -> HandoffRouteState.COMPLETED
        HybridRoutePhase.FAILED -> HandoffRouteState.FAILED
    }

    private fun identity(value: Any): String = Integer.toHexString(System.identityHashCode(value))
}

internal enum class RouteFinishMode {
    ACTIVITY_ONLY,
}

internal data class AppActivityOwnershipSnapshot(
    val liveAppActivities: Int,
    val liveVrHosts: Int,
)

internal object PlayerReleaseEligibility {
    fun shouldRelease(ownership: AppActivityOwnershipSnapshot): Boolean =
        ownership.liveAppActivities == 0 && ownership.liveVrHosts == 0
}

internal class AppActivityOwnershipRegistry<T : Any> {
    private val owners = IdentityHashMap<T, Boolean>()

    fun register(owner: T, isVrHost: Boolean) {
        owners[owner] = isVrHost
    }

    fun unregister(owner: T) {
        owners.remove(owner)
    }

    fun snapshot(): AppActivityOwnershipSnapshot = snapshotExcluding(null)

    fun snapshotExcluding(excluded: T?): AppActivityOwnershipSnapshot {
        var liveAppActivities = 0
        var liveVrHosts = 0
        owners.forEach { (owner, isVrHost) ->
            if (owner !== excluded) {
                liveAppActivities += 1
                if (isVrHost) liveVrHosts += 1
            }
        }
        return AppActivityOwnershipSnapshot(liveAppActivities, liveVrHosts)
    }
}

private class CoordinatedTransition(
    val state: HybridRouteTransition,
    val sourceActivity: WeakReference<Activity>,
    val applicationContext: Context,
    val playbackSnapshot: PlaybackSnapshot?,
    var destinationActivity: WeakReference<Activity>? = null,
) {
    fun clearReferences() {
        sourceActivity.clear()
        destinationActivity?.clear()
        destinationActivity = null
    }
}

internal enum class HybridRoutePhase {
    WAITING_FOR_SOURCE_DESTROY,
    READY_TO_LAUNCH_DESTINATION,
    WAITING_FOR_DESTINATION_SURFACE,
    WAITING_FOR_FIRST_FRAME,
    COMPLETED,
    FAILED,
}

internal class HybridRouteTransition(
    val id: Long,
    val source: HandoffTarget,
    val destination: HandoffTarget,
    private val destinationLaunchBeforeSourceDestroy: Boolean,
    private val requestedAtMs: Long,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
) {
    val sourceFinishMode: RouteFinishMode = RouteFinishMode.ACTIVITY_ONLY
    var phase: HybridRoutePhase = HybridRoutePhase.WAITING_FOR_SOURCE_DESTROY
        private set
    var sourceFinishRequested: Boolean = false
        private set
    var sourceDestroyed: Boolean = false
        private set
    var destinationLaunched: Boolean = false
        private set
    var destinationCreated: Boolean = false
        private set
    var sourceFinishGraceElapsed: Boolean = false
        private set
    var surfaceAttached: Boolean = false
        private set
    var firstFrame: Boolean = false
        private set
    var failureReason: String? = null
        private set

    val canAttachDestination: Boolean
        get() = phase == HybridRoutePhase.WAITING_FOR_DESTINATION_SURFACE ||
            phase == HybridRoutePhase.WAITING_FOR_FIRST_FRAME

    val canRequestSourceFinish: Boolean
        get() = destination != HandoffTarget.SYSTEM_2D_PANEL || destinationCreated || sourceFinishGraceElapsed

    fun markDestinationLaunched(): Boolean {
        if (destinationLaunched || phase == HybridRoutePhase.COMPLETED || phase == HybridRoutePhase.FAILED) return false
        if (!destinationLaunchBeforeSourceDestroy && !sourceDestroyed) return false
        destinationLaunched = true
        if (sourceDestroyed) phase = HybridRoutePhase.WAITING_FOR_DESTINATION_SURFACE
        return true
    }

    fun markDestinationCreated(): Boolean {
        if (!destinationLaunched || destinationCreated || phase == HybridRoutePhase.COMPLETED || phase == HybridRoutePhase.FAILED) {
            return false
        }
        destinationCreated = true
        return true
    }

    fun markSourceFinishGraceElapsed(): Boolean {
        if (sourceFinishGraceElapsed || phase == HybridRoutePhase.COMPLETED || phase == HybridRoutePhase.FAILED) return false
        sourceFinishGraceElapsed = true
        return true
    }

    fun markSourceFinishRequested(): Boolean {
        if (sourceFinishRequested || phase == HybridRoutePhase.COMPLETED || phase == HybridRoutePhase.FAILED) return false
        sourceFinishRequested = true
        return true
    }

    fun markSourceDestroyed(): Boolean {
        if (sourceDestroyed || phase == HybridRoutePhase.COMPLETED || phase == HybridRoutePhase.FAILED) return false
        sourceDestroyed = true
        phase = if (destinationLaunched) {
            HybridRoutePhase.WAITING_FOR_DESTINATION_SURFACE
        } else {
            HybridRoutePhase.READY_TO_LAUNCH_DESTINATION
        }
        return true
    }

    fun markSurfaceAttached(): Boolean {
        if (phase != HybridRoutePhase.WAITING_FOR_DESTINATION_SURFACE || surfaceAttached) return false
        surfaceAttached = true
        phase = HybridRoutePhase.WAITING_FOR_FIRST_FRAME
        return true
    }

    fun markFirstFrame(): Boolean {
        if (phase != HybridRoutePhase.WAITING_FOR_FIRST_FRAME || firstFrame) return false
        firstFrame = true
        phase = HybridRoutePhase.COMPLETED
        return true
    }

    fun fail(reason: String): Boolean {
        if (phase == HybridRoutePhase.COMPLETED || phase == HybridRoutePhase.FAILED) return false
        failureReason = reason
        phase = HybridRoutePhase.FAILED
        return true
    }

    fun elapsedMs(): Long = (clockMs() - requestedAtMs).coerceAtLeast(0)
}
