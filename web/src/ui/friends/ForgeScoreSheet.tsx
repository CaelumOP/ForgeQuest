import { scoreBreakdown, type ForgeScoreBreakdown } from '../../domain/forge-score'
import { t, useLang } from '../../i18n'
import { Sheet } from '../kit'

type Props = {
  open: boolean
  onClose: () => void
  done: number
  target: number
  prCount: number
  proteinDays: number
  breakdown?: ForgeScoreBreakdown
}

export function ForgeScoreSheet({ open, onClose, done, target, prCount, proteinDays, breakdown }: Props) {
  useLang()
  const b = breakdown ?? scoreBreakdown(done, target, prCount, proteinDays)
  return <Sheet open={open} onClose={onClose} title={t('score41_title')}>
    <div style={{ display: 'grid', gap: 'var(--sp-md)', overflowWrap: 'anywhere' }}>
      {target === 0 && <p style={{ margin: 0 }}>{t('score41_target')}</p>}
      <p style={{ margin: 0 }}>{t('score41_workouts', done, target, b.base)}</p>
      {b.prBonus > 0 && <p style={{ margin: 0 }}>{t('score41_prs', prCount, b.prBonus)}</p>}
      {b.proteinBonus > 0 && <p style={{ margin: 0 }}>{t('score41_protein', proteinDays, b.proteinBonus)}</p>}
      {b.extraBonus > 0 && <p style={{ margin: 0 }}>{t('score41_extra')}</p>}
      {b.prBonus + b.proteinBonus + b.extraBonus > b.bonus && <p class="muted">{t('score41_cap')}</p>}
      <strong dir="ltr">{t('score41_total', b.base, b.bonus, b.score)}</strong>
      <p class="muted">{t('score41_rule_base')}</p>
      <p class="muted">{t('score41_rule_bonus')}</p>
      <p class="muted">{t('score41_rule_tie')}</p>
    </div>
  </Sheet>
}

export function ForgeScoreInfoButton({ onClick }: { onClick: () => void }) {
  useLang()
  return <button type="button" class="kit-iconbtn" onClick={onClick} aria-label={t('score41_title')} style={{ minWidth: 44, minHeight: 44 }}>
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
      <circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7v1" />
    </svg>
  </button>
}

export function ForgeScoreButton({ score, done, target, onClick }: {
  score: number | null; done: number; target: number; onClick: () => void
}) {
  useLang()
  return <button type="button" class="kit-iconbtn" onClick={e => { e.stopPropagation(); onClick() }}
    aria-label={t('score41_score_label', score ?? '–')}
    style={{ display: 'flex', flexDirection: 'column', minWidth: 44, minHeight: 44, width: 'auto', height: 'auto', flexShrink: 0 }}>
    <strong>{score ?? '–'}</strong><span dir="ltr">{done}/{target}</span>
  </button>
}
