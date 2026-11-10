import type { ObjectDirective } from 'vue'
import { MICRO_INTERACTION as SETTINGS } from '../config/microMotion'

const disposers = new WeakMap<HTMLElement, () => void>()
const motionAllowed = () => document.documentElement.dataset.reducedMotion !== 'true'
  && !matchMedia('(prefers-reduced-motion: reduce)').matches
  && matchMedia('(hover: hover) and (pointer: fine)').matches

function accent(kind: 'magnet' | 'glow'): ObjectDirective<HTMLElement> {
  return {
    mounted(element) {
      let bounds: DOMRect | undefined, frame = 0, x = 0, y = 0
      element.classList.add(kind === 'magnet' ? 'micro-magnetic' : 'micro-glow')
      const reset = () => {
        cancelAnimationFrame(frame); frame = 0; bounds = undefined
        element.style.setProperty('--magnet-x', '0px'); element.style.setProperty('--magnet-y', '0px')
        element.style.setProperty('--pointer-active', '0')
      }
      const move = (event: PointerEvent) => {
        if (!motionAllowed() || event.pointerType !== 'mouse') { reset(); return }
        bounds ??= element.getBoundingClientRect()
        x = event.clientX - bounds.left; y = event.clientY - bounds.top
        if (frame) return
        frame = requestAnimationFrame(() => {
          frame = 0
          if (!bounds) return
          if (kind === 'magnet') {
            const limit = (value: number) => Math.max(-SETTINGS.magneticMax, Math.min(SETTINGS.magneticMax, value * SETTINGS.magneticStrength))
            element.style.setProperty('--magnet-x', `${limit(x - bounds.width / 2).toFixed(2)}px`)
            element.style.setProperty('--magnet-y', `${limit(y - bounds.height / 2).toFixed(2)}px`)
          } else {
            element.style.setProperty('--pointer-x', `${x.toFixed(1)}px`)
            element.style.setProperty('--pointer-y', `${y.toFixed(1)}px`)
            element.style.setProperty('--pointer-active', '1')
          }
        })
      }
      const media = matchMedia('(prefers-reduced-motion: reduce)')
      const preferences = new MutationObserver(reset)
      preferences.observe(document.documentElement, { attributes: true, attributeFilter: ['data-reduced-motion'] })
      element.addEventListener('pointerenter', move)
      element.addEventListener('pointermove', move)
      element.addEventListener('pointerleave', reset)
      if (kind === 'magnet') element.addEventListener('focusin', reset)
      window.addEventListener('resize', reset)
      media.addEventListener('change', reset)
      disposers.set(element, () => {
        reset(); preferences.disconnect()
        element.removeEventListener('pointerenter', move); element.removeEventListener('pointermove', move)
        element.removeEventListener('pointerleave', reset); element.removeEventListener('focusin', reset)
        window.removeEventListener('resize', reset); media.removeEventListener('change', reset)
      })
    },
    beforeUnmount(element) { disposers.get(element)?.(); disposers.delete(element) },
  }
}

export const vMagnetic = accent('magnet')
export const vGlow = accent('glow')
