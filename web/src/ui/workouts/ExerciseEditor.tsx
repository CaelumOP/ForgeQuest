import { useEffect, useState } from 'preact/hooks'
import { plural, t } from '../../i18n'
import type { ExerciseRow } from '../../data/db'
import { deleteExercise, mergeExercise, updateExercise, usage } from '../../data/workouts'
import type { ExerciseKind, Muscle } from '../../domain/muscle'
import { Button, Dialog, Sheet, toast } from '../kit'
import { ExerciseFields, ExercisePicker } from './ExercisePicker'

/**
 * Edit sheet for a custom exercise: rename, change kind and main muscle, merge into another, or delete
 * (Android ExerciseLibraryScreen). `onGone` fires after a merge or delete, when the exercise no longer exists.
 */
export function ExerciseEditor({ ex, library, onClose, onGone }: { ex: ExerciseRow | null; library: readonly ExerciseRow[]; onClose: () => void; onGone?: () => void }) {
  const [name, setName] = useState('')
  const [kind, setKind] = useState<ExerciseKind>('REPS')
  const [muscle, setMuscle] = useState<Muscle>('CHEST')
  const [use, setUse] = useState<{ sets: number; planItems: number } | null>(null)
  const [step, setStep] = useState<'edit' | 'merge' | 'delete'>('edit')

  useEffect(() => {
    if (!ex) return
    setName(ex.name); setKind(ex.kind); setMuscle(ex.primary[0] ?? 'CHEST'); setStep('edit'); setUse(null)
    let live = true
    usage(ex.id).then((u) => { if (live) setUse(u) })
    return () => { live = false }
  }, [ex?.id])

  if (!ex) return null
  const save = async () => {
    await updateExercise({ ...ex, name: name.trim() || ex.name, kind, primary: [muscle, ...ex.primary.filter((m) => m !== muscle).slice(0, 1)] })
    toast(t('lib_saved', name.trim() || ex.name))
    onClose()
  }
  return (
    <>
      <Sheet open={step === 'edit'} onClose={onClose} title={t('lib_edit')}>
        {use && <p class="wk-muted">{t('lib_usage', plural('lib_logged_sets', use.sets), plural('lib_plan_items', use.planItems))}</p>}
        <ExerciseFields nameLabel={t('label_name')} name={name} kind={kind} muscle={muscle} onName={setName} onKind={setKind} onMuscle={setMuscle} />
        <div class="wk-pair">
          <Button variant="tonal" onClick={() => setStep('merge')}>{t('lib_merge_into')}</Button>
          <Button variant="tonal" class="kit-danger" onClick={() => setStep('delete')}>{t('action_delete')}</Button>
          <Button onClick={save}>{t('action_save')}</Button>
        </div>
      </Sheet>
      <ExercisePicker
        open={step === 'merge'} title={t('lib_merge_title', ex.name)} library={library.filter((x) => x.id !== ex.id)}
        onClose={() => setStep('edit')}
        onPick={async (into) => { await mergeExercise(ex.id, into.id); toast(t('lib_merged', ex.name, into.name)); onClose(); onGone?.() }}
      />
      <Dialog
        open={step === 'delete'} destructive title={t('food_delete_title', ex.name)} message={t('lib_delete_body')}
        confirmLabel={t('action_delete')} onCancel={() => setStep('edit')}
        onConfirm={async () => {
          const ok = await deleteExercise(ex.id)
          toast(ok ? t('lib_deleted', ex.name) : t('lib_still_used', ex.name))
          if (ok) { onClose(); onGone?.() } else setStep('edit')
        }}
      />
    </>
  )
}
