export type ReorderStep = { order: number[]; offset: number; swapped: boolean }

/**
 * One step of dragging [id] by [offset] px from where it sits in [order]: once it has moved past half of its
 * neighbour's height the two swap places and the offset is taken back by that neighbour's height.
 */
export function stepReorder(order: number[], id: number, offset: number, heightOf: (id: number) => number): ReorderStep {
  const i = order.indexOf(id)
  if (i < 0) return { order, offset, swapped: false }
  const swap = (a: number, b: number, off: number): ReorderStep => {
    const next = order.slice()
    ;[next[a], next[b]] = [next[b], next[a]]
    return { order: next, offset: off, swapped: true }
  }
  const below = order[i + 1]
  const above = order[i - 1]
  if (offset > 0 && below != null && offset > heightOf(below) / 2) return swap(i, i + 1, offset - heightOf(below))
  if (offset < 0 && above != null && -offset > heightOf(above) / 2) return swap(i, i - 1, offset + heightOf(above))
  return { order, offset, swapped: false }
}
