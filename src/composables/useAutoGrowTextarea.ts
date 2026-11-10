import { nextTick, onBeforeUnmount, onMounted, watch, type Ref } from 'vue'
import { MICRO_INTERACTION } from '../config/microMotion'

export function useAutoGrowTextarea(element: Ref<HTMLTextAreaElement | undefined>, value: Ref<string>) {
  let frame = 0, disposed = false, observer: ResizeObserver | undefined, width = 0
  function resize() {
    const field = element.value
    if (!field || disposed) return
    field.style.height = 'auto'
    const height = Math.max(MICRO_INTERACTION.inputMinHeight, Math.min(MICRO_INTERACTION.inputMaxHeight, field.scrollHeight))
    field.style.height = `${height}px`
    field.style.overflowY = field.scrollHeight > MICRO_INTERACTION.inputMaxHeight ? 'auto' : 'hidden'
  }
  const queue = () => { cancelAnimationFrame(frame); frame = requestAnimationFrame(resize) }
  watch(value, () => nextTick(queue))
  onMounted(() => {
    resize()
    // Only width changes trigger another measurement; growing height cannot form an observer loop.
    observer = new ResizeObserver(entries => {
      const nextWidth = entries[0]?.contentRect.width ?? 0
      if (nextWidth !== width) { width = nextWidth; queue() }
    })
    if (element.value) observer.observe(element.value)
    document.fonts.ready.then(() => { if (!disposed) queue() })
  })
  onBeforeUnmount(() => { disposed = true; observer?.disconnect(); cancelAnimationFrame(frame) })
}
