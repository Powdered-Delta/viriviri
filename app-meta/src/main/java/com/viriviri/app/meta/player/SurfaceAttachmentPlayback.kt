package com.viriviri.app.meta.player

/** Reassert playback only after a newly attached output may have lost audio focus. */
internal fun shouldRequestPlaybackAfterAttach(replacedOutput: Boolean): Boolean = replacedOutput
