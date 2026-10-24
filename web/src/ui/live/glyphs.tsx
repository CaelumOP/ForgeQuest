// Small SVG icons for the live workout (Material paths, 24px grid).
type P = { size?: number }
const G = (d: string) => ({ size = 24 }: P) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false"><path d={d} /></svg>
)
export const PlayIcon = G('M8 5v14l11-7z')
export const FocusIcon = G('M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm-7 7H3v4c0 1.1.9 2 2 2h4v-2H5v-4zM5 5h4V3H5c-1.1 0-2 .9-2 2v4h2V5zm14-2h-4v2h4v4h2V5c0-1.1-.9-2-2-2zm0 16h-4v2h4c1.1 0 2-.9 2-2v-4h-2v4z')
export const ListIcon = G('M4 18h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2H4C2.9 6 2 6.9 2 8v8c0 1.1.9 2 2 2zM4 8h16v8H4V8z')
export const RepeatIcon = G('M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z')
export const MoreVert = G('M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z')
