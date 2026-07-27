package com.viriviri.app.meta.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView

class SurfaceHandoffTextureView(
    context: Context,
    private val playerManager: PlayerManager,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderSurface: Surface? = null

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Surface(surfaceTexture).also {
            renderSurface = it
            playerManager.attachSurface(it)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        renderSurface?.let { surface ->
            // A delayed disposal callback must not detach the newly attached target.
            playerManager.detachSurface(surface)
            surface.release()
        }
        renderSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
}
