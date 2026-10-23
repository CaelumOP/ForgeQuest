import { getLang, setLang, t } from '../i18n'
import { AddSquareIcon, ShareIcon } from './icons'

/** Safari and Home Screen apps keep separate storage (decision 9), so iOS must run from the Home Screen. */
export function needsInstallGate(): boolean {
  const ua = navigator.userAgent
  const ios = /iPad|iPhone|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)
  const standalone = matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true
  return ios && !standalone
}

export function InstallGate() {
  const other = getLang() === 'ar' ? 'en' : 'ar'
  return (
    <main class="gate">
      <button type="button" class="lang-toggle" lang={other} aria-label={t('lang_switch')} onClick={() => setLang(other)}>
        {t(`lang_name_${other}`)}
      </button>
      <img class="gate-icon" src={`${import.meta.env.BASE_URL}icon.svg`} alt="" width={88} height={88} />
      <h1>{t('gate_title')}</h1>
      <p class="muted">{t('gate_why')}</p>
      <ol class="gate-steps" aria-label={t('gate_steps_label')}>
        <li><span class="step-num" aria-hidden="true">1</span><span>{t('gate_step_share')}</span><span class="step-glyph"><ShareIcon /></span></li>
        <li><span class="step-num" aria-hidden="true">2</span><span>{t('gate_step_add')}</span><span class="step-glyph"><AddSquareIcon /></span></li>
        <li><span class="step-num" aria-hidden="true">3</span><span>{t('gate_step_open')}</span><img class="step-glyph" src={`${import.meta.env.BASE_URL}icon.svg`} alt="" width={22} height={22} /></li>
      </ol>
    </main>
  )
}
