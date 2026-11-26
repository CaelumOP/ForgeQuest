package com.forgequest.app.ui.workouts

/** Centerline offset, including both round caps so the visible gap remains 6 dp. */
internal fun restWaveTrackStart(width: Float, progress: Float): Float? {
    if (width <= 0f) return null
    val active = width * progress.coerceIn(0f, 1f)
    val start = if (active > 0f) active + 6f + 4f else 0f
    return start.takeIf { it < width }
}

/** Smoothstep reaches a flat tangent at each end, also calming very short waves. */
internal fun restWaveTaper(distance: Float, activeWidth: Float, wavelength: Float = 32f): Float {
    if (activeWidth <= 0f || wavelength <= 0f) return 0f
    val t = (minOf(distance, activeWidth - distance) / wavelength).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
