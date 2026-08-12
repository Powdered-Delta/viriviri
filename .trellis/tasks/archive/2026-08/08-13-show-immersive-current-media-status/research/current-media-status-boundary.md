# Immersive Current Media Status Boundary

The existing `mode_panel` is the compact system/current-media status region for
the first immersive implementation. It has no new Spatial registration or media
ownership role.

`ViriViriAppState` remains authoritative for selected recommendation and error.
The Spatial Activity observes that state and projects it through pure
`immersiveMediaStatus` before updating the already-created Android TextViews.

Playback/viewer errors override author detail but retain current title. Browse
list errors, including pagination failures, stay within the Browse list and do
not replace current-media status. Long title/detail text is bounded before it
reaches a fixed-size panel and is additionally single-line ellipsized in XML.

The existing Activity-owned flow collector is cancelled in `onDestroy`. The
status presenter neither loads media nor interacts with the player/Surface.
