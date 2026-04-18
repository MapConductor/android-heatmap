# HeatmapOverlayState

`HeatmapOverlayState` is a state holder for the `HeatmapOverlay` composable. It manages the
properties of the heatmap and triggers automatic re-rendering when any property changes.

Typically created with `remember { HeatmapOverlayState(...) }`. Use this class when you need to
read or modify heatmap properties programmatically after composition.

## Signature

```kotlin
class HeatmapOverlayState(
    radiusPx: Int = HeatmapDefaults.DEFAULT_RADIUS_PX,
    opacity: Double = HeatmapDefaults.DEFAULT_OPACITY,
    gradient: HeatmapGradient = HeatmapGradient.DEFAULT,
    maxIntensity: Double? = null,
    weightProvider: (HeatmapPointState) -> Double = { state -> state.weight },
)
```

## Parameters

- `radiusPx`
    - Type: `Int`
    - Default: `HeatmapDefaults.DEFAULT_RADIUS_PX` (`20`)
    - Description: The initial radius of each point in pixels.
- `opacity`
    - Type: `Double`
    - Default: `HeatmapDefaults.DEFAULT_OPACITY` (`0.7`)
    - Description: The initial opacity of the heatmap layer (`0.0` = transparent, `1.0` = opaque).
- `gradient`
    - Type: `HeatmapGradient`
    - Default: `HeatmapGradient.DEFAULT`
    - Description: The initial color gradient for rendering the heatmap.
- `maxIntensity`
    - Type: `Double?`
    - Default: `null`
    - Description: The initial maximum intensity for normalization. If `null`, the maximum is
      calculated automatically from the data.
- `weightProvider`
    - Type: `(HeatmapPointState) -> Double`
    - Default: `{ state -> state.weight }`
    - Description: A function that extracts a weight from a `HeatmapPointState`. Determines the
      intensity contribution of each point.

## Properties

All properties are backed by `mutableStateOf`. Changing any property causes `HeatmapOverlay` to
re-render automatically.

- `radiusPx` — Type: `Int`
- `opacity` — Type: `Double`
- `gradient` — Type: `HeatmapGradient`
- `maxIntensity` — Type: `Double?`
- `weightProvider` — Type: `(HeatmapPointState) -> Double`

## Methods

### copy

Creates a new `HeatmapOverlayState` with optionally overridden properties.

**Signature**

```kotlin
fun copy(
    radiusPx: Int = this.radiusPx,
    opacity: Double = this.opacity,
    gradient: HeatmapGradient = this.gradient,
    maxIntensity: Double? = this.maxIntensity,
    weightProvider: (HeatmapPointState) -> Double = this.weightProvider,
): HeatmapOverlayState
```

**Parameters**

- `radiusPx`
    - Type: `Int`
    - Description: The new radius in pixels. Defaults to the current `radiusPx`.
- `opacity`
    - Type: `Double`
    - Description: The new opacity. Defaults to the current `opacity`.
- `gradient`
    - Type: `HeatmapGradient`
    - Description: The new color gradient. Defaults to the current `gradient`.
- `maxIntensity`
    - Type: `Double?`
    - Description: The new maximum intensity. Defaults to the current `maxIntensity`.
- `weightProvider`
    - Type: `(HeatmapPointState) -> Double`
    - Description: The new weight provider function. Defaults to the current `weightProvider`.

**Returns**

- Type: `HeatmapOverlayState`
- Description: A new `HeatmapOverlayState` instance with the specified properties.

## Example

```kotlin
import androidx.compose.runtime.remember

@Composable
fun MyMapScreen() {
    val heatmapState = remember { HeatmapOverlayState() }

    // Modify properties dynamically in response to UI events
    // heatmapState.radiusPx = 30
    // heatmapState.opacity = 0.5

    HeatmapOverlay(state = heatmapState) {
        points.forEach { pointState ->
            HeatmapPoint(pointState)
        }
    }
}
```
