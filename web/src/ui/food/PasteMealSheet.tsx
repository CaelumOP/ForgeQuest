import { useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import { foodSkillPrompt } from '../../data/ai'
import { Button, Sheet, TextField, toast } from '../kit'
import { parseScan, type FoodScan } from './scan'

export function PasteMealSheet({ open, onClose, onParsed }: { open: boolean; onClose: () => void; onParsed: (scan: FoodScan) => void }) {
  const [text, setText] = useState('')
  const copySkill = () => {
    void navigator.clipboard?.writeText(foodSkillPrompt(getLang()))
    toast(t('bridge_prompt_copied'))
  }
  const pasteClipboard = async () => {
    try {
      const clip = await navigator.clipboard?.readText()
      if (clip) setText(clip)
    } catch { /* clipboard read denied */ }
  }
  const read = () => {
    const scan = parseScan(text)
    if (!scan || scan.items.length === 0) {
      toast(t(scan ? 'scan_err_no_food' : 'scan_err_unreadable'))
      return
    }
    setText('')
    onParsed(scan)
  }
  return (
    <Sheet open={open} onClose={onClose} title={t('food_paste_meal')}>
      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBlockEnd: '8px' }}>
        <Button variant="tonal" onClick={copySkill}>{t('meals_copy_gem')}</Button>
        <Button variant="tonal" onClick={() => void pasteClipboard()}>{t('food_w_paste_meal_btn')}</Button>
      </div>
      <TextField label={t('food_paste_meal')} multiline value={text} onInput={setText} hint={t('food_w_paste_meal_hint')} />
      <Button block disabled={!text.trim()} onClick={read}>{t('food_w_read_meal')}</Button>
    </Sheet>
  )
}
