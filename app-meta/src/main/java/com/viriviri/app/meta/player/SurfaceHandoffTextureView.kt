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
    private var surfaceWidth = 0
    private var surfaceHeight = 0

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
            val attached = playerManager.attachSurface(
                surface,
                target,
                transitionKey,
            )
            Log.i(
                TAG,
                "TextureView bound surface attach transition=$transitionKey target=${target.name} " +
                    "surface=${identity(surface)} size=${surfaceWidth}x$surfaceHeight success=$attached",
            )
            if (attachmentGate.markAttachment(attached)) {
                onSurfaceAttached()
            }
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        Log.i(
            TAG,
            "TextureView surface available transition=$transitionKey target=${target.name} size=${width}x$height",
        )
        Surface(surfaceTexture).also { surface ->
            renderSurface = surface
            attachmentGate.reset()
            val attached = playerManager.attachSurface(
                surface,
                target,
                transitionKey,
            )
            Log.i(
                TAG,
                "TextureView surface attach transition=$transitionKey target=${target.name} " +
                    "surface=${identity(surface)} size=${width}x$height success=$attached",
            )
            if (attachmentGate.markAttachment(attached)) {
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
                    "surface=${identity(surface)} size=${surfaceWidth}x$surfaceHeight",
            )
            playerManager.detachSurface(surface, target, transitionKey)
            surface.release()
        }
        renderSurface = null
        surfaceWidth = 0
        surfaceHeight = 0
        attachmentGate.reset()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        val surface = renderSurface
        if (surface != null && attachmentGate.markFirstFrame()) {
            Log.i(
                TAG,
                "TextureView first texture update transition=$transitionKey target=${target.name} " +
                    "surface=${identity(surface)} size=${surfaceWidth}x$surfaceHeight",
            )
            onFirstFrame()
        }
    }

    private companion object {
        const val TAG = "ViriviriPlayerPoC"

        fun identity(value: Any): String = Integer.toHexString(System.identityHashCode(value))
    }
}
