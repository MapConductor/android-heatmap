# HeatmapGradientStop

A data class that represents a single color stop within a `HeatmapGradient`. Each stop pairs a
relative position with a color.

## Signature

```kotlin
data class HeatmapGradientStop(
    val position: Double,
    val color: Int,
)
```

## Parameters

- `position`
    - Type: `Double`
    - Description: The relative position of the color stop. `0.0` is the start and `1.0` is the
      end of the gradient.
- `color`
    - Type: `Int`
    - Description: The ARGB color integer for this stop. Use `android.graphics.Color` to create
      this value.

## Example

```kotlin
import android.graphics.Color

// A stop at 50% of the gradient with a yellow color
val yellowStop = HeatmapGradientStop(position = 0.5, color = Color.YELLOW)

// A stop at the beginning with a semi-transparent blue color
val blueStop = HeatmapGradientStop(position = 0.0, color = Color.argb(128, 0, 0, 255))
```

---

# HeatmapGradient

A class that defines the color gradient used to render the heatmap. Constructed from a list of
`HeatmapGradientStop` objects.

## Signature

```kotlin
class HeatmapGradient(stops: List<HeatmapGradientStop>)
```

## Description

Upon creation, the gradient validates and sorts the provided stops by `position`.

**Constructor validation:**
- The list of stops must not be empty.
- The `position` of each stop must be within the inclusive range `[0.0, 1.0]`.

An `IllegalArgumentException` is thrown if these conditions are not met.

## Parameters

- `stops`
    - Type: `List<HeatmapGradientStop>`
    - Description: **Required.** One or more `HeatmapGradientStop` objects that define the
      gradient. The list is sorted by `position` internally.

## Properties

- `stops`
    - Type: `List<HeatmapGradientStop>`
    - Description: The validated and sorted list of gradient stops.

## Methods

### `colors()`

Returns an array of color integers from the gradient stops, sorted by position.

**Signature**

```kotlin
fun colors(): IntArray
```

**Returns**

- Type: `IntArray`
- Description: An array of ARGB color integers sorted by stop position. Useful for APIs that
  require a simple array of colors.

## Companion Object

### `DEFAULT`

A predefined gradient that transitions from green (at position `0.2`) to red (at position `1.0`).

**Signature**

```kotlin
val DEFAULT: HeatmapGradient
```

## Example

```kotlin
import android.graphics.Color

// Create a custom three-color gradient (blue → yellow → red)
val customGradient = HeatmapGradient(
    stops = listOf(
        HeatmapGradientStop(position = 0.0, color = Color.BLUE),
        HeatmapGradientStop(position = 0.5, color = Color.YELLOW),
        HeatmapGradientStop(position = 1.0, color = Color.RED),
    )
)

// Use the predefined default gradient
val defaultGradient = HeatmapGradient.DEFAULT
```

---

# HeatmapDefaults

A singleton object that provides default constant values for heatmap properties.

## Signature

```kotlin
object HeatmapDefaults
```

## Properties

- `DEFAULT_RADIUS_PX`
    - Type: `Int`
    - Value: `20`
    - Description: The default radius in pixels for each data point on the heatmap.
- `DEFAULT_OPACITY`
    - Type: `Double`
    - Value: `0.7`
    - Description: The default opacity of the heatmap layer (`0.0` = transparent, `1.0` = opaque).
- `DEFAULT_MAX_ZOOM`
    - Type: `Int`
    - Value: `22`
    - Description: The default maximum map zoom level at which the heatmap is rendered.

## Example

```kotlin
val radiusPx = userProvidedRadius ?: HeatmapDefaults.DEFAULT_RADIUS_PX
val opacity = userProvidedOpacity ?: HeatmapDefaults.DEFAULT_OPACITY
```
