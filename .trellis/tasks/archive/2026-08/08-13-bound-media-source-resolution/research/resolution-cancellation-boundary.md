# Playback Resolution Cancellation Boundary

`BilibiliPlaybackProvider.createMediaSource` is currently synchronous and uses
`HttpURLConnection`. `ViriViriAppState` therefore cancels the coroutine job
that owns the request result, but cannot guarantee interruption of an already
blocking connection. Each HTTP connection already has a 15-second provider
network timeout.

Starting a selection or explicit retry cancels the prior resolution job and
assigns a new request id before running the next attempt. Cancellation causes
the superseded result to exit without changing UI state, MediaSource, player,
or Surface. A `withTimeout(45_000)` around the full resolution path prevents
the current UI state from remaining in `Loading video...`; only the current
attempt maps timeout to `Video source resolution timed out` and enables its
existing Retry action.

Replacing the provider with a fully cancellable HTTP implementation is outside
this increment. It must preserve the same latest-request result ownership and
one-player/one-active-Surface invariant.
