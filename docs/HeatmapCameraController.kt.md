# HeatmapCameraController

A lightweight controller that links map camera movements to a `HeatmapTileRenderer`. It listens
for camera position changes and forwards the current zoom level to the renderer, allowing the
heatmap's appearance to adapt dynamically as the user zooms.

This controller implements `OverlayControllerInterface` but is focused solely on camera event
handling. It does not manage data points, state, or click events.

## Signature

```kotlin
class HeatmapCameraController(
    private val renderer: HeatmapTileRenderer,
) : OverlayControllerInterface<Unit, Unit, Unit>
```

## Constructor

### `HeatmapCameraController(renderer)`

Creates an instance of `HeatmapCameraController`.

**Parameters**

- `renderer`
    - Type: `HeatmapTileRenderer`
    - Description: **Required.** The heatmap renderer to update with camera zoom changes.

## Methods

### `onCameraChanged`

Called by the map framework whenever the camera position changes. Extracts the zoom level from
the new camera position and passes it to the `HeatmapTileRenderer`.

**Signature**

```kotlin
override suspend fun onCameraChanged(mapCameraPosition: MapCameraPosition)
```

**Parameters**

- `mapCameraPosition`
    - Type: `MapCameraPosition`
    - Description: The new position and zoom level of the map camera.

## Interface Methods (no-op)

The following methods from `OverlayControllerInterface` are implemented as no-ops. This
controller's scope is limited to camera handling.

- `add(data: List<Unit>)` — Does not add data to the overlay.
- `update(state: Unit)` — Does not update any state.
- `clear()` — Does not clear any data.
- `find(position: GeoPointInterface)` — Always returns `null`.
- `destroy()` — No native resources to clean up.

## Example

```kotlin
val renderer = HeatmapTileRenderer()
val cameraController = HeatmapCameraController(renderer)

// The controller is registered with the map internally by HeatmapOverlay.
// When the user zooms or pans, onCameraChanged is called automatically,
// updating the renderer with the new zoom level.
```
