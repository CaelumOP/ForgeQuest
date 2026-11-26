/** Centerline offset, including both round caps so the visible gap remains 6 px. */
export function restWaveTrackStart(width: number, progress: number): number | null {
  if (width <= 0) return null
  const active = width * Math.max(0, Math.min(1, progress))
  const start = active > 0 ? active + 6 + 4 : 0
  return start < width ? start : null
}

/** Smoothstep reaches a flat tangent at each end, also calming very short waves. */
export function restWaveTaper(distance: number, activeWidth: number, wavelength = 32): number {
  if (activeWidth <= 0 || wavelength <= 0) return 0
  const t = Math.max(0, Math.min(1, Math.min(distance, activeWidth - distance) / wavelength))
  return t * t * (3 - 2 * t)
}
