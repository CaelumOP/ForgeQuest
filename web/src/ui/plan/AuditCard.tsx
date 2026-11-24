import { useState } from 'preact/hooks'
import { intlLocale, t, useLang, type Lang } from '../../i18n'
import type { Finding } from '../../domain/routine-auditor'
import { Button, Card, SectionHeader } from '../kit'
import { InfoIcon } from '../workouts/glyphs'
import { dayName, muscleLabel } from '../workouts/helpers'

function findingText(f: Finding, lang: Lang): string {
  const muscle = f.muscle ? muscleLabel(f.muscle) : ''
  const day = f.dayOfWeek != null && f.dayOfWeek >= 1 && f.dayOfWeek <= 7
    ? dayName(f.dayOfWeek, lang) : t('plan_audit_day_fallback', (f.dayIndex ?? 0) + 1)
  const number = new Intl.NumberFormat(intlLocale(lang), { maximumFractionDigits: 1 })
  switch (f.kind) {
    case 'ABOVE_MRV': return t('plan_audit_above_mrv', muscle, number.format(f.value), number.format(f.limit))
    case 'BELOW_MEV': return t('plan_audit_below_mev', muscle, number.format(f.value), number.format(f.limit))
    case 'PUSH_PULL': return t('plan_audit_push_pull', f.value, f.limit)
    case 'ISOLATION_FIRST': return t('plan_audit_isolation_first', day, muscle)
    case 'JUNK_VOLUME': return t('plan_audit_junk_volume', muscle, f.value, day, f.limit)
    case 'RECOVERY_OVERLAP': return t('plan_audit_recovery_overlap', muscle, f.limit)
  }
}

export function AuditCard({ findings }: { findings: readonly Finding[] }) {
  const lang = useLang()
  const [expanded, setExpanded] = useState(false)
  if (findings.length === 0) return null
  const visible = expanded ? findings : findings.slice(0, 3)
  const rows = visible.map((finding) => ({
    finding,
    text: findingText(finding, lang),
    warning: ['ABOVE_MRV', 'RECOVERY_OVERLAP', 'JUNK_VOLUME'].includes(finding.kind),
  }))
  return (
    <Card>
      <div style={{ display: 'grid', gap: 'var(--sp-sm)' }}>
        <SectionHeader title={t('plan_audit_title')} />
        {rows.map(({ finding, text, warning }) => (
          <div key={`${finding.kind}-${finding.dayIndex}-${finding.muscle}`} style={{ display: 'flex', alignItems: 'start', gap: 'var(--sp-xs)' }}>
            <span role="img" aria-label={t(warning ? 'cd_audit_warning' : 'cd_audit_info')}
              style={{ display: 'flex', flexShrink: 0, color: warning ? 'var(--coral)' : 'var(--text-2)' }}>
              {warning ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z" />
                </svg>
              ) : <InfoIcon size={18} />}
            </span>
            <span style={{ minInlineSize: 0, overflowWrap: 'anywhere' }}>{text}</span>
          </div>
        ))}
        {findings.length > 3 && (
          <Button variant="text" onClick={() => setExpanded(!expanded)}>
            {expanded ? t('plan_audit_show_less') : t('plan_audit_show_all', findings.length)}
          </Button>
        )}
      </div>
    </Card>
  )
}
