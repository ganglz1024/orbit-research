import { nextTick, onBeforeUnmount, onMounted, watch, type Ref } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { HOME_MOTION } from '../config/homeMotion'
import { useResearchStore } from '../stores/research'

gsap.registerPlugin(ScrollTrigger)

/** Lower-page entrances are independent of the single pinned scene and play only once. */
export function useHomeReveals(root: Ref<HTMLElement | undefined>) {
  const store = useResearchStore()
  let media: gsap.MatchMedia | undefined
  let disposed = false
  const tweens = new Map<HTMLElement, gsap.core.Tween>()

  function revealFocused(event: FocusEvent) {
    const section = (event.target as HTMLElement).closest<HTMLElement>('[data-home-reveal]')
    if (section) tweens.get(section)?.progress(1)
  }

  function setup() {
    if (!root.value || disposed) return
    media?.revert()
    tweens.clear()
    const element = root.value
    media = gsap.matchMedia()
    media.add({ all: '(min-width: 0px)', reduce: '(prefers-reduced-motion: reduce)' }, context => {
      if (context.conditions?.reduce || store.reducedMotion) return
      element.querySelectorAll<HTMLElement>('[data-home-reveal]').forEach((target, index) => {
        const closing = target.dataset.homeReveal === 'closing'
        // Keep hit targets stationary: focus can finish the visual entrance during pointerdown.
        const visuals = target.matches('.workflow-grid article')
          ? Array.from(target.children).filter(child => !child.matches('a, button'))
          : target
        const tween = gsap.fromTo(visuals, { opacity: 0, y: closing ? 0 : HOME_MOTION.revealOffset + (index % 3 - 1) * 8 }, {
          opacity: 1, y: 0, duration: HOME_MOTION.revealSeconds, ease: 'power2.out',
          scrollTrigger: { trigger: target, start: 'top 92%', once: true },
        })
        tweens.set(target, tween)
      })
    }, element)
  }

  watch(() => store.reducedMotion, () => nextTick(setup))
  onMounted(() => { setup(); root.value?.addEventListener('focusin', revealFocused) })
  onBeforeUnmount(() => {
    disposed = true
    root.value?.removeEventListener('focusin', revealFocused)
    media?.revert()
    tweens.clear()
  })
}
