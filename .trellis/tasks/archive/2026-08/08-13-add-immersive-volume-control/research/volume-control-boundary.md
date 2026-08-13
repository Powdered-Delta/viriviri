# Immersive Volume Control Boundary

The UX contract includes volume in the transport action strip. This increment
adds it only to the existing `controls_id` Android panel.

The app-side `PlaybackVolumeControl` exposes fixed choices `0%`, `25%`, `50%`,
`75%`, `100%`. It normalizes unknown/non-finite values to display `Vol 100%`
without writing them back. The Activity's existing Media3 listener refreshes the
label using `onVolumeChanged`.

Menu selection executes only `player.volume = volume` on the process-wide
Player. It does not prepare/reload/seek/set media source/create player/attach
a Surface, or create/change a Spatial panel/entity/scene transform. Popup
rendering and hand/controller input remain a manual Quest check, like the
existing speed menu.
