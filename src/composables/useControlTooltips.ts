import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { MICRO_INTERACTION } from '../config/microMotion'

/** One delegated tooltip layer for existing controls across every route. */
export function useControlTooltips() {
  const route = useRoute()
  const selector = '.icon-button[aria-label], .canvas-controls button[aria-label], .send-button[aria-label], [data-tooltip]'
  let trigger: HTMLElement | undefined, bubble: HTMLElement | undefined, delay: ReturnType<typeof setTimeout> | undefined
  let originalTitle: string | null = null, previousDescription: string | null = null
  const retiring = new Map<HTMLElement, ReturnType<typeof setTimeout>>()
  function hide() {
    clearTimeout(delay)
    if (trigger && bubble) {
      if (previousDescription === null) trigger.removeAttribute('aria-describedby')
      else trigger.setAttribute('aria-describedby', previousDescription)
      if (originalTitle !== null) trigger.setAttribute('title', originalTitle)
      const node = bubble
      node.classList.remove('is-visible')
      const duration = Number.parseFloat(getComputedStyle(node).transitionDuration) * 1000 || 0
      retiring.set(node, setTimeout(() => { node.remove(); retiring.delete(node) }, duration))
    }
    trigger = undefined; bubble = undefined
  }
  function schedule(target: HTMLElement) {
    if (trigger === target) return
    hide(); trigger = target
    delay = setTimeout(() => {
      if (!target.isConnected || trigger !== target) return
      const text = target.dataset.tooltip || target.getAttribute('aria-label') || target.getAttribute('title')
      if (!text) return
      originalTitle = target.getAttribute('title'); previousDescription = target.getAttribute('aria-describedby')
      target.removeAttribute('title')
      const node = document.createElement('div')
      node.className = 'orbit-tooltip'; node.id = `orbit-tip-${crypto.randomUUID()}`
      node.setAttribute('role', 'tooltip'); node.textContent = text
      document.body.append(node); bubble = node
      const rect = target.getBoundingClientRect(), tip = node.getBoundingClientRect()
      node.style.left = `${Math.max(8, Math.min(innerWidth - tip.width - 8, rect.left + (rect.width - tip.width) / 2))}px`
      node.style.top = `${rect.top - tip.height - 8 >= 8 ? rect.top - tip.height - 8 : rect.bottom + 8}px`
      target.setAttribute('aria-describedby', [previousDescription, node.id].filter(Boolean).join(' '))
      node.getBoundingClientRect() // Commit the initial opacity before the short entrance.
      node.classList.add('is-visible')
    }, MICRO_INTERACTION.tooltipDelay)
  }
  const targetOf = (event: Event) => event.target instanceof Element ? event.target.closest<HTMLElement>(selector) : null
  function enter(event: Event) { const target = targetOf(event); if (target) schedule(target) }
  function leave(event: Event) {
    const next = (event as MouseEvent | FocusEvent).relatedTarget
    if (trigger && (!(next instanceof Node) || !trigger.contains(next))) hide()
  }
  function key(event: KeyboardEvent) { if (event.key === 'Escape') hide() }
  watch(() => route.fullPath, hide)
  onMounted(() => {
    document.addEventListener('pointerover', enter); document.addEventListener('pointerout', leave)
    document.addEventListener('focusin', enter); document.addEventListener('focusout', leave)
    document.addEventListener('pointerdown', hide, true); document.addEventListener('keydown', key)
    window.addEventListener('scroll', hide, true); window.addEventListener('resize', hide)
  })
  onBeforeUnmount(() => {
    hide()
    for (const [node, timer] of retiring) { clearTimeout(timer); node.remove() }
    retiring.clear()
    document.removeEventListener('pointerover', enter); document.removeEventListener('pointerout', leave)
    document.removeEventListener('focusin', enter); document.removeEventListener('focusout', leave)
    document.removeEventListener('pointerdown', hide, true); document.removeEventListener('keydown', key)
    window.removeEventListener('scroll', hide, true); window.removeEventListener('resize', hide)
  })
}
