package com.viriviri.app.meta.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.viriviri.core.state.HandoffTarget

class SurfaceHandoffTextureView(
    context: Context,
    private val playerManager: PlayerManager,
    private val target: HandoffTarget,
    transitionKey: Long?,
    onSurfaceAttached: () -> Unit,
    onFirstFrame: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderSurface: Surface? = null
    private var transitionKey = transitionKey
    private var onSurfaceAttached = onSurfaceAttached
    private var onFirstFrame = onFirstFrame
    private val attachmentGate = SurfaceAttachmentGate()

    init {
        surfaceTextureListener = this
    }

    fun bindHandoff(
        transitionKey: Long?,
        onSurfaceAttached: () -> Unit,
        onFirstFrame: () -> Unit,
    ) {
        if (this.transitionKey == transitionKey) return

        this.transitionKey = transitionKey
        this.onSurfaceAttached = onSurfaceAttached
        this.onFirstFrame = onFirstFrame
        attachmentGate.reset()
        renderSurface?.takeIf(Surface::isValid)?.let { surface ->
            if (attachmentGate.markAttachment(playerManager.attachSurface(surface, target, transitionKey))) {
                onSurfaceAttached()
            }
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Surface(surfaceTexture).also { surface ->
            renderSurface = surface
            attachmentGate.reset()
            if (attachmentGate.markAttachment(playerManager.attachSurface(surface, target, transitionKey))) {
                onSurfaceAttached()
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        renderSurface?.let { surface ->
            // A delayed disposal callback must not detach the newly attached target.
            Log.i(
                TAG,
                "Surface destroyed transition=$transitionKey target=${target.name} " +
                    "surface=${identity(surface)}",
            )
            playerManager.detachSurface(surface, target, transitionKey)
            surface.release()
        }
        renderSurface = null
        attachmentGate.reset()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        if (renderSurface != null && attachmentGate.markFirstFrame()) onFirstFrame()
    }

    private companion object {
        const val TAG = "ViriviriPlayerPoC"

        fun identity(value: Any): String = Integer.toHexString(System.identityHashCode(value))
    }
}
