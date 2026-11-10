<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { HOME_MOTION, HOME_STEPS } from '../config/homeMotion'
import { useResearchStore } from '../stores/research'

gsap.registerPlugin(ScrollTrigger)
const emit = defineEmits<{ seek: [index: number] }>()
const store = useResearchStore()
const root = ref<HTMLElement>()
const active = ref(0)
let media: gsap.MatchMedia | undefined
let disposed = false

function setup() {
  if (!root.value || disposed) return
  media?.revert()
  media = gsap.matchMedia()
  const element = root.value
  media.add({ all: '(min-width: 0px)', reduce: '(prefers-reduced-motion: reduce)' }, context => {
    if (context.conditions?.reduce || store.reducedMotion) return
    const select = gsap.utils.selector(element)
    gsap.fromTo(select('.research-steps-line > span'), { scaleX: 0 }, {
      scaleX: 1, ease: 'none',
      scrollTrigger: {
        id: 'orbit-home-steps', trigger: element, start: 'top 85%', end: 'bottom 40%', scrub: true,
        onUpdate(self) {
          const next = Math.min(3, Math.floor(self.progress * 4))
          if (active.value !== next) active.value = next
        },
      },
    })
    gsap.from(select('.research-step-visual'), {
      y: 16, opacity: 0, duration: HOME_MOTION.revealSeconds, stagger: 0.08,
      scrollTrigger: { trigger: element, start: 'top 92%', once: true },
    })
  }, element)
}

function seek(index: number) {
  active.value = index
  emit('seek', index)
}

// Keyboard focus always exposes a step even before its decorative entrance has run.
function revealFocused(event: FocusEvent) {
  const target = (event.target as HTMLElement).closest('button')?.querySelector('.research-step-visual')
  if (target) gsap.getTweensOf(target).forEach(tween => tween.progress(1))
}

watch(() => store.reducedMotion, () => nextTick(setup))
onMounted(setup)
onBeforeUnmount(() => { disposed = true; media?.revert() })
</script>

<template>
  <section ref="root" class="research-steps-section" aria-label="四步研究流程">
    <div class="research-steps-intro"><span class="eyebrow">FOLLOW YOUR CURIOSITY</span><p>把刚刚看见的线索，带进自己的研究。</p></div>
    <nav class="research-steps" aria-label="研究流程阶段" @focusin="revealFocused">
      <div class="research-steps-line" aria-hidden="true"><span></span></div>
      <button v-for="(step, index) in HOME_STEPS" :key="step.title" type="button" :class="{ 'is-current': active === index, 'is-passed': active > index }" :aria-current="active === index ? 'step' : undefined" @click="seek(index)">
        <span class="research-step-visual"><span class="research-step-number">0{{ index + 1 }}</span><strong>{{ step.title }}</strong><span class="research-step-caption">{{ step.caption }}</span></span>
      </button>
    </nav>
  </section>
</template>

<style scoped>
.research-steps-section { max-width: 1160px; margin: 0 auto; padding: 64px 8px 0; }
.research-steps-intro { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 26px; }
.research-steps-intro > p { font-size: 14px; color: var(--muted); }
.research-steps { position: relative; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 28px; padding-top: 24px; }
.research-steps-line { position: absolute; left: 0; right: 0; top: 0; height: 1px; background: var(--border); }
.research-steps-line > span { display: block; height: 100%; background: var(--purple); transform-origin: left; }
.research-steps button { position: relative; text-align: left; padding: 8px 0 16px; border-radius: 4px; }
.research-step-visual { display: grid; gap: 12px; }
.research-step-number { font-size: 12px; font-variant-numeric: tabular-nums; color: var(--subtle); }
.research-steps strong { font-size: 18px; font-weight: 500; }
.research-step-caption { color: var(--muted); font-size: 13px; line-height: 1.8; }
.research-steps .is-current strong, .research-steps .is-current .research-step-number, .research-steps .is-passed .research-step-number { color: var(--purple-deep); }
.research-steps .is-current::before { content: ''; position: absolute; top: -29px; left: 0; height: 9px; width: 9px; border-radius: 50%; border: 2px solid var(--landing-bg); background: var(--purple); }
.research-steps button:focus-visible { outline: 2px solid var(--focus); outline-offset: 5px; }
@media (max-width: 1200px) { .research-steps-section { margin-inline: 32px; } }
@media (max-width: 700px) {
  .research-steps-section { margin-inline: 24px; padding-top: 40px; }
  .research-steps-intro { display: grid; gap: 12px; }
  .research-steps { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
  .research-steps button { padding: 8px 0; min-height: 116px; }
  .research-steps .is-current::before { display: none; }
  .research-steps strong { font-size: 17px; }
}
</style>
