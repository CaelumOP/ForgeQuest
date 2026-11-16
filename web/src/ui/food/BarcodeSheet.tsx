import { useEffect, useRef, useState } from 'preact/hooks'
import { t } from '../../i18n'
import { Button, Sheet, TextField } from '../kit'
import { lookupBarcode, type BarcodeProduct } from './barcode'

type BarcodeDetectorLike = { detect: (src: CanvasImageSource | HTMLVideoElement) => Promise<Array<{ rawValue?: string }>> }
type BarcodeDetectorCtor = new (opts?: { formats?: string[] }) => BarcodeDetectorLike

export function BarcodeSheet({ open, onClose, onFound }: { open: boolean; onClose: () => void; onFound: (p: BarcodeProduct) => void }) {
  const supported = typeof window !== 'undefined' && 'BarcodeDetector' in window && !!navigator.mediaDevices?.getUserMedia
  const videoRef = useRef<HTMLVideoElement>(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const lookup = async (raw: string) => {
    const clean = raw.trim()
    if (!clean || busy) return
    setBusy(true)
    setError('')
    try {
      const product = await lookupBarcode(clean)
      if (!product) { setError(t('barcode_missing_title')); setBusy(false); return }
      setBusy(false)
      onFound(product)
    } catch {
      setError(t('barcode_offline_title'))
      setBusy(false)
    }
  }

  useEffect(() => {
    if (!open) { setCode(''); setError(''); setBusy(false); return }
    if (!supported) return
    let stopped = false
    let stream: MediaStream | null = null
    let timer = 0
    const start = async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        if (stopped) { stream.getTracks().forEach((tr) => tr.stop()); return }
        const video = videoRef.current
        if (!video) return
        video.srcObject = stream
        await video.play()
        const Ctor = (window as unknown as { BarcodeDetector: BarcodeDetectorCtor }).BarcodeDetector
        const detector = new Ctor({ formats: ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128'] })
        const tick = async () => {
          if (stopped || !videoRef.current) return
          try {
            const hits = await detector.detect(videoRef.current)
            const hit = hits.find((h) => h.rawValue)?.rawValue
            if (hit) { setCode(hit); void lookup(hit); return }
          } catch { /* keep scanning */ }
          if (!stopped) timer = window.setTimeout(() => { void tick() }, 350)
        }
        timer = window.setTimeout(() => { void tick() }, 350)
      } catch {
        if (!stopped) setError(t('food_w_barcode_unsupported'))
      }
    }
    void start()
    return () => {
      stopped = true
      if (timer) clearTimeout(timer)
      if (stream) stream.getTracks().forEach((tr) => tr.stop())
    }
  }, [open, supported])

  return (
    <Sheet open={open} onClose={onClose} title={t('barcode_scan')}>
      {supported ? (
        <>
          <p class="food-note">{t('barcode_hint')}</p>
          <video ref={videoRef} playsInline muted style={{ width: '100%', maxHeight: '220px', borderRadius: '12px', background: '#000', objectFit: 'cover' }} />
        </>
      ) : (
        <p class="food-note">{t('food_w_barcode_unsupported')}</p>
      )}
      <TextField label={t('food_w_barcode_code')} value={code} onInput={setCode} />
      {error && <p class="food-note" role="alert" style={{ color: 'var(--coral)' }}>{error}</p>}
      <Button block disabled={!code.trim() || busy} loading={busy} onClick={() => void lookup(code)}>
        {busy ? t('barcode_looking') : t('food_w_barcode_lookup')}
      </Button>
    </Sheet>
  )
}
