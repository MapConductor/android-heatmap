# HeatmapPointState

A stateful class representing a single data point within a heatmap layer.

This class holds the geographical position, weight, and optional metadata for a heatmap point.
It is designed for use with Jetpack Compose — its properties are backed by `mutableStateOf` and
will trigger recomposition when changed.

## Signature

```kotlin
class HeatmapPointState(
    position: GeoPointInterface,
    weight: Double = 1.0,
    id: String? = null,
    extra: Serializable? = null,
) : ComponentState
```

## Parameters

- `position`
    - Type: `GeoPointInterface`
    - Description: **Required.** The geographical location of the heatmap point.
- `weight`
    - Type: `Double`
    - Default: `1.0`
    - Description: The intensity of the point. Higher values contribute more to the heatmap.
- `id`
    - Type: `String?`
    - Default: `null`
    - Description: An optional unique identifier. If not provided, a stable ID is generated from
      `position` and `extra`.
- `extra`
    - Type: `Serializable?`
    - Default: `null`
    - Description: Optional serializable data attached to the point, such as metadata or a model ID.

## Properties

- `id`
    - Type: `String`
    - Description: The unique identifier for the point.
- `position`
    - Type: `GeoPointInterface`
    - Description: The geographical position of the point. Backed by `mutableStateOf` — updating
      this value moves the point on the heatmap.
- `weight`
    - Type: `Double`
    - Description: The weight of the point. Backed by `mutableStateOf` — updating this value
      changes the point's intensity.
- `extra`
    - Type: `Serializable?`
    - Description: Optional extra data associated with the point. Backed by `mutableStateOf`.

## Methods

### `fingerPrint()`

Generates a compact, hash-based snapshot of the point's current state for efficient change
detection.

**Signature**

```kotlin
fun fingerPrint(): HeatmapPointFingerPrint
```

**Returns**

- Type: `HeatmapPointFingerPrint`
- Description: An object containing the hash codes of the point's current properties.

### `asFlow()`

Creates a `Flow` that emits a new `HeatmapPointFingerPrint` whenever any property of the
`HeatmapPointState` changes. The flow only emits when the state has genuinely changed.

**Signature**

```kotlin
fun asFlow(): Flow<HeatmapPointFingerPrint>
```

**Returns**

- Type: `Flow<HeatmapPointFingerPrint>`
- Description: A Kotlin Flow that emits a new fingerprint on each state change.

## Example

```kotlin
import com.mapconductor.heatmap.HeatmapPointState

val heatmapPoint = HeatmapPointState(
    position = GeoPoint(latitude = 40.7128, longitude = -74.0060),
    weight = 5.0,
)

// Observe state changes
val job = launch {
    heatmapPoint.asFlow().collect { fingerprint ->
        println("State changed: $fingerprint")
    }
}

// Modify weight at runtime — triggers the flow
heatmapPoint.weight = 10.0

job.cancel()
```

---

# HeatmapPointFingerPrint

A data class that represents a snapshot of a `HeatmapPointState` using the hash codes of its
properties. Used for efficient change detection in `asFlow()`.

## Signature

```kotlin
data class HeatmapPointFingerPrint(
    val id: Int,
    val position: Int,
    val weight: Int,
    val extra: Int,
)
```

## Properties

- `id`
    - Type: `Int`
    - Description: The hash code of the `HeatmapPointState`'s `id` property.
- `position`
    - Type: `Int`
    - Description: The hash code of the `HeatmapPointState`'s `position` property.
- `weight`
    - Type: `Int`
    - Description: The hash code of the `HeatmapPointState`'s `weight` property.
- `extra`
    - Type: `Int`
    - Description: The hash code of the `HeatmapPointState`'s `extra` property.
