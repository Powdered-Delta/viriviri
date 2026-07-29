package com.viriviri.app.meta.player

/** Keeps TextureView readiness callbacks tied to a verified player output. */
internal class SurfaceAttachmentGate {
    private var attachmentVerified = false
    private var firstFrameReported = false

    fun reset() {
        attachmentVerified = false
        firstFrameReported = false
    }

    fun markAttachment(verifiedCurrentOutput: Boolean): Boolean {
        attachmentVerified = verifiedCurrentOutput
        return attachmentVerified
    }

    fun markFirstFrame(): Boolean {
        if (!attachmentVerified || firstFrameReported) return false

        firstFrameReported = true
        return true
    }
}
