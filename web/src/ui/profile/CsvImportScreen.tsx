import { useMemo, useRef, useState } from 'preact/hooks'
import { t } from '../../i18n'
import { useLibrary } from '../../data/workouts'
import {
  executeCsvImport, normalizeExerciseName, previewCsvImport, type CsvPreview, type ExerciseMapping,
} from '../../data/import/csvImport'
import type { ExerciseRow } from '../../data/db'
import { Button, Card, Chip, Sheet, TextField, toast } from '../kit'

export function CsvImportScreen({ onDone }: { param?: string; onDone?: () => void }) {
  const fileRef = useRef<HTMLInputElement>(null)
  const library = useLibrary()
  const byId = useMemo(() => new Map(library.map((e) => [e.id, e])), [library])
  const [preview, setPreview] = useState<CsvPreview | null>(null)
  const [mappings, setMappings] = useState<Record<string, ExerciseMapping>>({})
  const [pickingFor, setPickingFor] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [busy, setBusy] = useState(false)

  const onFile = async (f: File | undefined) => {
    if (!f) return
    setBusy(true)
    try {
      const next = await previewCsvImport(await f.text())
      setMappings({})
      setPreview(next)
    } catch {
      toast(t('prog41_csv_error'))
    } finally {
      setBusy(false)
    }
  }

  const runImport = async () => {
    if (!preview || busy) return
    setBusy(true)
    try {
      const res = await executeCsvImport(preview, new Map(Object.entries(mappings)))
      if (res.importedSets === 0) {
        toast(t('prog41_csv_nothing'))
      } else {
        toast(t('prog41_csv_done', res.importedWorkouts, res.importedSets))
        if (onDone) onDone()
        else if (typeof window !== 'undefined') window.history.back()
      }
    } catch {
      toast(t('prog41_csv_error'))
    } finally {
      setBusy(false)
    }
  }

  const unmatched = useMemo(() => {
    if (!preview) return [] as string[]
    return [...preview.exerciseMatches.values()]
      .filter((m) => m.status !== 'MATCHED')
      .map((m) => m.rawName)
      .sort()
  }, [preview])

  const filteredLib = useMemo(() => {
    const q = normalizeExerciseName(query)
    const pref = new Set(pickingFor ? preview?.exerciseMatches.get(pickingFor)?.candidates ?? [] : [])
    const list = q
      ? library.filter((e) => normalizeExerciseName(e.name).includes(q) || normalizeExerciseName(e.aliases ?? '').includes(q) || e.id.includes(q))
      : [...library].sort((a, b) => Number(pref.has(b.id)) - Number(pref.has(a.id)))
    return list.slice(0, 40)
  }, [library, query, pickingFor, preview])

  return (
    <div class="content pf">
      <Card>
        <h2 class="pf-h3">{t('prog41_csv_import_title')}</h2>
        <p class="pf-s" style={{ marginBlockStart: '4px' }}>{t('prog41_csv_import_hint')}</p>
        <div class="pf-actions">
          <Button block loading={busy} onClick={() => fileRef.current?.click()}>{t('prog41_csv_pick_file')}</Button>
        </div>
        <input
          ref={fileRef}
          type="file"
          accept=".csv,text/csv,text/plain"
          hidden
          onChange={(e) => {
            const el = e.currentTarget as HTMLInputElement
            void onFile(el.files?.[0])
            el.value = ''
          }}
        />
      </Card>

      {preview && (
        <Card>
          <h3 class="pf-h3">{t('prog41_csv_detected', preview.source === 'STRONG' ? 'Strong' : preview.source === 'HEVY' ? 'Hevy' : 'FitNotes')}</h3>
          <p class="pf-s" style={{ color: 'var(--blue)', marginBlockStart: '6px' }}>
            {t('prog41_csv_preview_summary', preview.totalWorkouts, preview.newSetsCount, preview.duplicateSetsCount)}
          </p>
          <div class="pf-actions">
            <Button block loading={busy} onClick={() => void runImport()}>{t('prog41_csv_import_btn')}</Button>
          </div>
        </Card>
      )}

      {preview && unmatched.length > 0 && (
        <Card>
          <h3 class="pf-h3">{t('prog41_csv_unmatched_title')}</h3>
          <p class="pf-s" style={{ marginBlockStart: '4px' }}>{t('prog41_csv_unmatched_hint')}</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBlockStart: '10px' }}>
            {unmatched.map((raw) => {
              const cur = mappings[raw]
              const status = cur?.kind === 'use'
                ? t('prog41_csv_mapped_to', byId.get(cur.exerciseId)?.name ?? cur.exerciseId)
                : cur?.kind === 'create'
                  ? t('prog41_csv_map_create')
                  : cur?.kind === 'skip'
                    ? t('prog41_csv_map_skip')
                    : null
              return (
                <div key={raw} style={{ padding: '12px', borderRadius: 'var(--r-md)', background: 'var(--card-high)' }}>
                  <div class="pf-t">{raw}</div>
                  {status && <div class="pf-s" style={{ color: 'var(--blue)' }}>{status}</div>}
                  <div class="pf-actions">
                    <Button variant="tonal" onClick={() => { setQuery(''); setPickingFor(raw) }}>{t('prog41_csv_map_pick')}</Button>
                    <Chip
                      label={t('prog41_csv_map_create')}
                      selected={cur?.kind === 'create'}
                      onClick={() => setMappings((m) => ({ ...m, [raw]: { kind: 'create', name: raw } }))}
                    />
                    <Chip
                      label={t('prog41_csv_map_skip')}
                      selected={cur?.kind === 'skip'}
                      onClick={() => setMappings((m) => ({ ...m, [raw]: { kind: 'skip' } }))}
                    />
                  </div>
                </div>
              )
            })}
          </div>
        </Card>
      )}

      <Sheet open={pickingFor != null} onClose={() => setPickingFor(null)} title={t('prog41_csv_map_pick')}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <TextField label={t('prog41_csv_search_hint')} type="search" value={query} onInput={setQuery} />
          <div style={{ display: 'flex', flexDirection: 'column', maxBlockSize: '320px', overflowY: 'auto' }}>
            {filteredLib.map((ex: ExerciseRow) => (
              <button
                key={ex.id}
                type="button"
                class="kit-row kit-row-tap"
                onClick={() => {
                  if (pickingFor) {
                    setMappings((m) => ({ ...m, [pickingFor]: { kind: 'use', exerciseId: ex.id } }))
                    setPickingFor(null)
                  }
                }}
              >
                <span class="kit-row-text">
                  <span class="kit-row-title">{ex.name}</span>
                  <span class="kit-row-sub">{ex.primary.map((p) => t(`muscle_${p.toLowerCase()}`)).join(', ')}</span>
                </span>
              </button>
            ))}
          </div>
        </div>
      </Sheet>
    </div>
  )
}
