package com.mapconductor.heatmap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mapconductor.core.ComponentState
import com.mapconductor.core.StateMutationSignal
import com.mapconductor.core.features.GeoPointInterface
import java.io.Serializable

class HeatmapPointState(
    position: GeoPointInterface,
    weight: Double = 1.0,
    id: String? = null,
    extra: Serializable? = null,
) : ComponentState {
    override val id =
        (
            id ?: heatmapPointId(
                listOf(
                    position.hashCode(),
                    extra?.hashCode() ?: 0,
                ),
            )
        ).toString()

    /**
     * Writes to the fields below are announced here rather than discovered by
     * reading them all back. See [StateMutationSignal].
     */
    override val mutations = StateMutationSignal()

    var position by mutations.notifying(position)
    var weight by mutations.notifying(weight)
    var extra by mutations.notifying(extra)

    private fun heatmapPointId(hashCodes: List<Int>): Int =
        hashCodes.reduce { result, hashCode ->
            31 * result + hashCode
        }

    fun fingerPrint(): HeatmapPointFingerPrint =
        HeatmapPointFingerPrint(
            id = id.hashCode(),
            position = position.hashCode(),
            weight = weight.hashCode(),
            extra = extra?.hashCode() ?: 0,
        )
}

data class HeatmapPointFingerPrint(
    val id: Int,
    val position: Int,
    val weight: Int,
    val extra: Int,
)
