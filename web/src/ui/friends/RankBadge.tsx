import { t, type Lang } from '../../i18n'
import { englishOrdinalSuffix, formatOrdinal, rankTier } from './rank'

export interface RankBadgeProps {
  rank: number | null
  total: number
  lang: Lang
  /** Hide accessibility label for unsynced ("–") badges inside rows that already announce status. */
  hideUnsyncedA11y?: boolean
}

export function RankBadge({ rank, total, lang, hideUnsyncedA11y = false }: RankBadgeProps) {
  const tier = rankTier(rank)
  const isAr = lang === 'ar'
  const suffix = rank != null && !isAr ? englishOrdinalSuffix(rank).toUpperCase() : null
  const visualNum = rank != null ? String(rank) : '–'

  const a11yLabel =
    rank != null && total > 0
      ? t('friends_rank_cd', isAr ? String(rank) : formatOrdinal(rank), total)
      : hideUnsyncedA11y
        ? undefined
        : t('friends_badge_not_synced')

  return (
    <span
      class={`fr-rank-badge fr-rank-${tier}`}
      role={a11yLabel ? 'img' : undefined}
      aria-label={a11yLabel}
      aria-hidden={a11yLabel ? undefined : 'true'}
    >
      <span class="fr-rank-vis" aria-hidden="true">
        <span class="fr-rank-num">{visualNum}</span>
        {suffix && <span class="fr-rank-suf">{suffix}</span>}
      </span>
    </span>
  )
}
