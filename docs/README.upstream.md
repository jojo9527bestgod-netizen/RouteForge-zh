<p align="center">
  <img src="docs/logo.svg" width="128" height="128" alt="RouteForge logo">
</p>

<h1 align="center">RouteForge</h1>

<p align="center">
  An Android app that simulates device location (mock location / GPS injection) with real
  road-following routes — computed fully offline, on-device, no server or API key required.
</p>

## Why

Most "fake GPS" apps on the Play Store connect waypoints with a straight line and ignore basic physical consistency (speed, bearing, accuracy), which makes the simulated position trivially inconsistent with real sensor data. RouteForge aims to do this properly:

- **Real roads, by choice** — when a route is ready to play, RouteForge checks whether every leg can be driven along real streets. If so, you choose between **Guided** (follows real roads) and **Free-roam** (goes straight through your points, for off-road or unrestricted movement); if not, only Free-roam is offered — never a broken option.
- **Physically realistic movement** — speed, bearing, and accuracy stay internally consistent.
- **Guided setup** — a step-by-step flow for the Developer Options ritual Android requires to enable mock location.
- **Always-on joystick** — free-direction movement is available at any time, including as a deliberate, confirmed interrupt of a route that's currently playing.
- **Edit a route live, not just before it starts** — reorder, add, move, or delete waypoints while a route is actively playing, not only while planning it; only the portion still ahead of your current position is ever editable mid-run.
- **Control it without opening the app** — pause, resume, or stop an active simulation straight from its notification, including from the lock screen.
- **Search for a place, not just tap a map** — look up an address or place name (OpenStreetMap/Nominatim), ranked by relevance and distance to you, then add it as a waypoint, teleport to it, or save it as a favorite.
- **Favorites, not just one-off routes** — save a single location or a whole planned route by name and reuse it later, without re-tapping every point again.
- **Offline map data you control** — download only the regions you actually need, with a storage quota you set and manage yourself.
- **No ads, open source.**

## Basic usage

- **Build a route** — tap the map to add waypoints; use the waypoint list to drag-reorder or delete, tap a waypoint to edit its coordinates, undo any change.
- **Choose how it plays** — Guided (real roads) and Free-roam (straight lines) are offered based on what's actually computable for your points; pick one to lock it in for that playback session. Before playing, RouteForge checks whether the map data Guided needs is downloaded and offers to fetch it — offline map coverage is computed on demand for anywhere in the world (BRouter's global tile grid), not limited to a fixed list of regions.
- **Edit mid-run** — reorder, add, move, or delete waypoints on a route that's already playing, from the same shared waypoint list used while planning; the simulation recomputes and keeps going.
- **Import/export** — load a route from a JSON or GPX file, or export any route you've built back to either format.
- **Tune playback** — a shared speed selector (0-150 km/h) and an execution mode (once, N times, or loop), both adjustable before and during playback.
- **Joystick** — drag to move freely in any direction at any time; touching it during an active route asks for confirmation first, then hands off control from where the route was interrupted.
- **Control from the notification** — pause, resume, or stop the running simulation directly from its persistent notification (works from the lock screen, no need to unlock); tapping the notification itself jumps back into the app on the Simulate screen. A completed route briefly shows its finish before the session ends on its own.
- **Favorite a location** — save any point you tap while building a route, then teleport straight to it later (with confirmation) from the star button on the Plan Route screen.
- **Favorite a whole route** — once you've placed two or more waypoints, save the set as a named favorite route — before you've even computed or played it. Reopen it later from the Saved tab, which computes it and loads it straight onto the Simulate screen.
- **Manage offline regions** — the Settings tab shows how much space downloaded map data is using, lets you set a storage quota, and the region catalog lets you download or delete coverage for specific areas.
- **Search** — tap the magnifying-glass icon in the bottom bar from any tab to look up an address or place. From Plan Route, picking a result adds it as a waypoint; from anywhere else, you're offered to teleport to it or save it as a favorite.

## Status

Early stage — actively being designed and built. Not yet on the Play Store.

## License

MIT — see [LICENSE](LICENSE).
