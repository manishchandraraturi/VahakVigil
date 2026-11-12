package com.manish.vahakvigil.domain.location

/** Pure position/progress calculations over recorded GPS points; no interpolation or live fixes. */
object RecordedRoutePlayback {
    fun indexForProgress(progress: Float, pointCount: Int): Int? {
        if (pointCount <= 0) return null
        if (pointCount == 1) return 0
        val normalized = progress.coerceIn(0f, 1f)
        return (normalized * (pointCount - 1)).toInt().coerceIn(0, pointCount - 1)
    }

    fun progressForIndex(index: Int?, pointCount: Int): Float = when {
        pointCount <= 1 || index == null -> 0f
        else -> index.coerceIn(0, pointCount - 1).toFloat() / (pointCount - 1)
    }

    fun nextIndex(index: Int, pointCount: Int): Int? =
        if (pointCount <= 0 || index >= pointCount - 1) null else (index + 1).coerceAtLeast(0)
}
