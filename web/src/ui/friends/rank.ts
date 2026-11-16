export type RankTier = 'gold' | 'silver' | 'bronze' | 'neutral'

/** English lowercase ordinal suffix ("st", "nd", "rd", "th") for `rank`. */
export function englishOrdinalSuffix(rank: number): 'st' | 'nd' | 'rd' | 'th' {
  const abs = Math.abs(rank)
  const mod100 = abs % 100
  if (mod100 >= 11 && mod100 <= 13) return 'th'
  switch (abs % 10) {
    case 1:
      return 'st'
    case 2:
      return 'nd'
    case 3:
      return 'rd'
    default:
      return 'th'
  }
}

/** Formats `rank` as an English ordinal (e.g. 1 -> "1st", 12 -> "12th"). */
export function formatOrdinal(rank: number): string {
  return `${rank}${englishOrdinalSuffix(rank)}`
}

/** Subtle medal tier for top 3; neutral for 4th+ and unsynced (`null`). */
export function rankTier(rank: number | null): RankTier {
  if (rank === 1) return 'gold'
  if (rank === 2) return 'silver'
  if (rank === 3) return 'bronze'
  return 'neutral'
}
