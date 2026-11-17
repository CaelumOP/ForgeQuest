import { useCallback, useEffect, useRef, useState } from 'preact/hooks'
import { t } from '../../i18n'
import type { FoodRow } from '../../data/db'
import { CameraIcon, EditIcon, PhotoLibraryIcon } from '../icons'
import { Button, Sheet, TextField, haptics } from '../kit'
import { beep, unlockAudio } from '../live/feedback'
import {
  barcodeVariants,
  createConsecutiveFilter,
  decodeBarcodeFromImage,
  getBarcodeDetector,
  isValidBarcodeCheckDigit,
  lookupBarcode,
  normalizeScannedCode,
  type BarcodeProduct,
} from './barcode'

const SCAN_INTERVAL_MS = 80 // ~12.5 fps
const BEEP_PREF_KEY = 'fq.scan.beep'

const TorchIcon = ({ on }: { on: boolean }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path
      d={
        on
          ? 'M6 2h12v3l-2 3v14H8V8L6 5V2zm6 9.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z'
          : 'M3.27 2 2 3.27 8 9.27V22h8v-4.73l4.73 4.73L22 20.73 3.27 2zM18 5V2H6v.73L10.73 7.46 16 12.73V8l2-3z'
      }
    />
  </svg>
)

const SoundIcon = ({ on }: { on: boolean }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path
      d={
        on
          ? 'M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z'
          : 'M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3 3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4 9.91 6.09 12 8.18V4z'
      }
    />
  </svg>
)

function loadBeepPref(): boolean {
  try {
    return localStorage.getItem(BEEP_PREF_KEY) !== '0'
  } catch {
    return true
  }
}

type CameraProblem = 'denied' | 'no_camera' | 'failed' | null

type SheetState =
  | { kind: 'scanning' }
  | { kind: 'looking'; code: string }
  | { kind: 'own'; food: FoodRow }
  | { kind: 'missing'; code: string; offline: boolean }

type VideoWithVfc = HTMLVideoElement & {
  requestVideoFrameCallback?: (cb: () => void) => number
  cancelVideoFrameCallback?: (id: number) => void
}

type TrackCapabilitiesWithExtras = MediaTrackCapabilities & {
  focusMode?: string[]
  torch?: boolean
}

export function BarcodeSheet({
  open,
  aiReady,
  onClose,
  onFound,
  onLogOwn,
  onEditOwn,
  onCreateManual,
  onLabelRead,
}: {
  open: boolean
  aiReady: boolean
  onClose: () => void
  onFound: (p: BarcodeProduct) => void
  onLogOwn: (f: FoodRow) => void
  onEditOwn: (f: FoodRow) => void
  onCreateManual: (code: string) => void
  onLabelRead: (code: string) => void
}) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const photoInputRef = useRef<HTMLInputElement>(null)
  const trackRef = useRef<MediaStreamTrack | null>(null)
  const lookupCtlRef = useRef<AbortController | null>(null)
  const retryTokenRef = useRef(0)

  const [state, setState] = useState<SheetState>({ kind: 'scanning' })
  const [camProblem, setCamProblem] = useState<CameraProblem>(null)
  const [torchSupported, setTorchSupported] = useState(false)
  const [torchOn, setTorchOn] = useState(false)
  const [beepOn, setBeepOn] = useState(loadBeepPref)
  const beepRef = useRef(beepOn)
  beepRef.current = beepOn

  const [manualOpen, setManualOpen] = useState(false)
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [photoBusy, setPhotoBusy] = useState(false)
  const [camRestartKey, setCamRestartKey] = useState(0)

  const notifyDetected = useCallback(() => {
    haptics.check()
    if (beepRef.current) beep(1046, 0.08)
  }, [])

  const runLookup = useCallback(
    async (raw: string) => {
      const clean = normalizeScannedCode(raw) ?? raw.trim()
      if (!clean) return
      lookupCtlRef.current?.abort()
      const ctl = new AbortController()
      lookupCtlRef.current = ctl
      setError('')
      setState({ kind: 'looking', code: clean })

      try {
        const res = await lookupBarcode(clean, { signal: ctl.signal })
        if (ctl.signal.aborted) return
        if (res.status === 'own') {
          setState({ kind: 'own', food: res.food })
          return
        }
        if (res.status === 'cached' || res.status === 'online') {
          onFound(res.product)
          return
        }
        setState({ kind: 'missing', code: res.code, offline: res.status === 'offline' })
      } catch {
        if (!ctl.signal.aborted) {
          setState({ kind: 'missing', code: clean, offline: true })
        }
      }
    },
    [onFound],
  )

  // Reset state when sheet closes or opens
  useEffect(() => {
    if (!open) {
      lookupCtlRef.current?.abort()
      lookupCtlRef.current = null
      setState({ kind: 'scanning' })
      setCamProblem(null)
      setTorchSupported(false)
      setTorchOn(false)
      setManualOpen(false)
      setCode('')
      setError('')
      setPhotoBusy(false)
      return
    }
    unlockAudio()
  }, [open])

  // Camera + scan loop lifecycle
  useEffect(() => {
    if (!open || state.kind !== 'scanning') return

    let disposed = false
    let stream: MediaStream | null = null
    let frameHandle: number | null = null
    let frameMode: 'vfc' | 'raf' | null = null
    let lastScanAt = 0
    let detecting = false
    const filter = createConsecutiveFilter(2)

    const stopStream = () => {
      if (frameHandle != null) {
        const video = videoRef.current as VideoWithVfc | null
        if (frameMode === 'vfc' && video?.cancelVideoFrameCallback) {
          video.cancelVideoFrameCallback(frameHandle)
        } else {
          cancelAnimationFrame(frameHandle)
        }
        frameHandle = null
        frameMode = null
      }
      const tr = trackRef.current
      if (tr) {
        try {
          void tr.applyConstraints({ advanced: [{ torch: false } as MediaTrackConstraintSet] }).catch(() => {})
        } catch {
          // ignore
        }
        trackRef.current = null
      }
      if (stream) {
        stream.getTracks().forEach((track) => track.stop())
        stream = null
      }
      if (videoRef.current) {
        videoRef.current.srcObject = null
      }
      setTorchOn(false)
      setTorchSupported(false)
    }

    const isHidden = () => document.visibilityState === 'hidden'

    const startCamera = async () => {
      if (isHidden()) return
      if (!navigator.mediaDevices?.getUserMedia) {
        setCamProblem('no_camera')
        setManualOpen(true)
        return
      }
      setCamProblem(null)
      filter.reset()

      const myToken = ++retryTokenRef.current
      try {
        const [detector, media] = await Promise.all([
          getBarcodeDetector(),
          navigator.mediaDevices.getUserMedia({
            audio: false,
            video: {
              facingMode: { ideal: 'environment' },
              width: { ideal: 1920 },
              height: { ideal: 1080 },
            },
          }),
        ])
        if (disposed || myToken !== retryTokenRef.current || isHidden()) {
          media.getTracks().forEach((tr) => tr.stop())
          return
        }
        stream = media
        const track = media.getVideoTracks()[0] ?? null
        trackRef.current = track

        if (track) {
          const caps = track.getCapabilities?.() as TrackCapabilitiesWithExtras | undefined
          setTorchSupported(Boolean(caps?.torch))
          if (caps?.focusMode?.includes('continuous')) {
            void track
              .applyConstraints({ advanced: [{ focusMode: 'continuous' } as MediaTrackConstraintSet] })
              .catch(() => {})
          }
          track.addEventListener('ended', () => {
            if (!disposed) setCamProblem('failed')
          })
        }

        const video = videoRef.current as VideoWithVfc | null
        if (!video) {
          stopStream()
          return
        }
        video.srcObject = media
        await video.play()
        if (disposed || myToken !== retryTokenRef.current) {
          stopStream()
          return
        }

        const schedule = () => {
          if (disposed) return
          if (video.requestVideoFrameCallback) {
            frameMode = 'vfc'
            frameHandle = video.requestVideoFrameCallback(onFrame)
          } else {
            frameMode = 'raf'
            frameHandle = requestAnimationFrame(onFrame)
          }
        }

        const onFrame = () => {
          frameHandle = null
          if (disposed || document.visibilityState === 'hidden') return
          const now = Date.now()
          if (!detecting && now - lastScanAt >= SCAN_INTERVAL_MS && video.readyState >= 2 && video.videoWidth > 0) {
            lastScanAt = now
            detecting = true
            void detector
              .detect(video)
              .then((hits) => {
                if (disposed) return
                let matched: string | null = null
                for (const hit of hits) {
                  if (!hit.rawValue) continue
                  const normalized = normalizeScannedCode(hit.rawValue, hit.format)
                  if (normalized) {
                    matched = normalized
                    break
                  }
                }
                const confirmed = filter.push(matched)
                if (confirmed) {
                  notifyDetected()
                  setCode(confirmed)
                  stopStream()
                  void runLookup(confirmed)
                }
              })
              .catch(() => {
                // Keep scanning across transient frame errors.
              })
              .finally(() => {
                detecting = false
              })
          }
          schedule()
        }

        schedule()
      } catch (err) {
        stopStream()
        if (disposed || myToken !== retryTokenRef.current) return
        const name = (err as { name?: string })?.name ?? ''
        if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
          setCamProblem('denied')
          setManualOpen(true)
        } else if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
          setCamProblem('no_camera')
          setManualOpen(true)
        } else {
          setCamProblem('failed')
        }
      }
    }

    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        stopStream()
      } else if (!disposed && open && state.kind === 'scanning') {
        void startCamera()
      }
    }

    document.addEventListener('visibilitychange', onVisibility)
    void startCamera()

    return () => {
      disposed = true
      document.removeEventListener('visibilitychange', onVisibility)
      stopStream()
    }
  }, [open, state.kind, camRestartKey, notifyDetected, runLookup])

  const toggleTorch = async () => {
    const track = trackRef.current
    if (!track || !torchSupported) return
    const next = !torchOn
    try {
      await track.applyConstraints({ advanced: [{ torch: next } as MediaTrackConstraintSet] })
      setTorchOn(next)
    } catch {
      setTorchSupported(false)
      setTorchOn(false)
    }
  }

  const toggleBeep = () => {
    unlockAudio()
    const next = !beepOn
    setBeepOn(next)
    try {
      localStorage.setItem(BEEP_PREF_KEY, next ? '1' : '0')
    } catch {
      // ignore
    }
  }

  const handleTapCamera = () => {
    unlockAudio()
    if (camProblem) {
      setCamRestartKey((k) => k + 1)
      return
    }
    const track = trackRef.current
    if (track) {
      const caps = track.getCapabilities?.() as TrackCapabilitiesWithExtras | undefined
      if (caps?.focusMode?.includes('continuous')) {
        void track
          .applyConstraints({ advanced: [{ focusMode: 'continuous' } as MediaTrackConstraintSet] })
          .catch(() => {})
      }
    }
  }

  const handlePhotoPick = async (e: Event) => {
    unlockAudio()
    const input = e.target as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file || photoBusy) return
    setPhotoBusy(true)
    setError('')
    try {
      const detected = await decodeBarcodeFromImage(file)
      if (!detected) {
        setError(t('food_w_barcode_photo_none'))
        return
      }
      notifyDetected()
      setCode(detected)
      await runLookup(detected)
    } catch {
      setError(t('food_w_barcode_photo_none'))
    } finally {
      setPhotoBusy(false)
    }
  }

  const handleManualLookup = () => {
    unlockAudio()
    const trimmed = code.trim()
    if (!trimmed) return
    const digitsOnly = /^\d+$/.test(trimmed)
    if (digitsOnly && (trimmed.length === 8 || trimmed.length === 12 || trimmed.length === 13 || trimmed.length === 14)) {
      if (!isValidBarcodeCheckDigit(trimmed)) {
        setError(t('food_w_barcode_bad_code'))
        return
      }
    }
    const variants = barcodeVariants(trimmed)
    if (!variants.length) {
      setError(t('food_w_barcode_bad_code'))
      return
    }
    void runLookup(variants[0])
  }

  const camMessage =
    camProblem === 'denied'
      ? t('food_w_barcode_denied')
      : camProblem === 'no_camera'
        ? t('food_w_barcode_no_camera')
        : camProblem === 'failed'
          ? t('food_w_barcode_camera_err')
          : ''

  return (
    <Sheet open={open} onClose={onClose} title={t('barcode_scan')}>
      <input
        ref={photoInputRef}
        class="scan-file"
        type="file"
        accept="image/*"
        capture="environment"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => void handlePhotoPick(e)}
      />

      {state.kind === 'scanning' && (
        <>
          <div class="bc-stage">
            <video ref={videoRef} playsInline muted class="bc-video" />
            {!camProblem && (
              <button
                type="button"
                class="bc-tap-layer"
                aria-label={t('barcode_hint')}
                onClick={handleTapCamera}
              >
                <span class="bc-roi" aria-hidden="true" />
              </button>
            )}
            {camProblem && (
              <div class="bc-cam-msg" role="status">
                <p>{camMessage}</p>
                {camProblem !== 'no_camera' && (
                  <Button variant="tonal" onClick={handleTapCamera}>
                    {t('food_w_barcode_retry')}
                  </Button>
                )}
              </div>
            )}
            <div class="bc-overlay-bar">
              {torchSupported ? (
                <button
                  type="button"
                  class="bc-round-btn"
                  aria-label={t('cd_light')}
                  aria-pressed={torchOn}
                  onClick={() => void toggleTorch()}
                >
                  <TorchIcon on={torchOn} />
                </button>
              ) : (
                <span />
              )}
              <button
                type="button"
                class="bc-round-btn"
                aria-label={t('food_w_barcode_beep')}
                aria-pressed={beepOn}
                onClick={toggleBeep}
              >
                <SoundIcon on={beepOn} />
              </button>
            </div>
          </div>

          {!camProblem && <p class="food-note">{t('barcode_hint')}</p>}

          <div class="bc-fallbacks">
            <Button
              variant="tonal"
              icon={<PhotoLibraryIcon size={18} />}
              loading={photoBusy}
              onClick={() => photoInputRef.current?.click()}
            >
              {t('food_w_barcode_photo')}
            </Button>
            <Button variant="tonal" onClick={() => setManualOpen((v) => !v)}>
              {t('food_w_barcode_type')}
            </Button>
          </div>

          {(manualOpen || camProblem != null) && (
            <div class="bc-manual">
              <TextField
                label={t('food_w_barcode_code')}
                value={code}
                onInput={(v) => {
                  setError('')
                  setCode(v)
                }}
              />
              <Button block disabled={!code.trim()} onClick={handleManualLookup}>
                {t('food_w_barcode_lookup')}
              </Button>
            </div>
          )}
        </>
      )}

      {state.kind === 'looking' && (
        <div class="scan-busy" role="status" aria-live="polite">
          <span class="kit-morph kit-morph-teal" aria-hidden="true" />
          <span>{t('barcode_looking')}</span>
          <span class="food-note" dir="ltr">
            {state.code}
          </span>
        </div>
      )}

      {state.kind === 'own' && (
        <div class="bc-card">
          <span class="food-note">{t('food_w_barcode_found_saved')}</span>
          <h3 class="bc-card-title">{state.food.name}</h3>
          {state.food.place && <span class="food-note">{state.food.place}</span>}
          <p class="bc-card-macros">
            {[
              t('food_kcal', state.food.kcal),
              t('food_g_protein', Math.round(state.food.protein)),
              state.food.serving || null,
            ]
              .filter(Boolean)
              .join(' · ')}
          </p>
          <div class="bc-card-actions">
            <Button block onClick={() => onLogOwn(state.food)}>
              {t('food_log_it')}
            </Button>
            <Button variant="tonal" block icon={<EditIcon size={18} />} onClick={() => onEditOwn(state.food)}>
              {t('food_edit_title')}
            </Button>
            <Button variant="text" block onClick={() => setState({ kind: 'scanning' })}>
              {t('barcode_scan_again')}
            </Button>
          </div>
        </div>
      )}

      {state.kind === 'missing' && (
        <div class="bc-card">
          <h3 class="bc-card-title">
            {state.offline ? t('barcode_offline_title') : t('barcode_missing_title')}
          </h3>
          <p class="food-note" dir="ltr">
            {state.code}
          </p>
          <p class="bc-card-body">
            {state.offline
              ? aiReady
                ? t('barcode_offline_body')
                : t('food_w_barcode_missing_noai')
              : aiReady
                ? t('barcode_missing_body')
                : t('food_w_barcode_missing_noai')}
          </p>
          <div class="bc-card-actions">
            {aiReady && (
              <Button block icon={<CameraIcon size={18} />} onClick={() => onLabelRead(state.code)}>
                {t('barcode_label_photo')}
              </Button>
            )}
            <Button
              variant={aiReady ? 'tonal' : 'filled'}
              block
              onClick={() => onCreateManual(state.code)}
            >
              {t('food_w_barcode_create')}
            </Button>
            <Button variant="text" block onClick={() => setState({ kind: 'scanning' })}>
              {t('barcode_scan_again')}
            </Button>
          </div>
        </div>
      )}

      {error && (
        <p class="food-note bc-err" role="alert">
          {error}
        </p>
      )}
    </Sheet>
  )
}
