<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { defaultQuestion, paperById } from '../data/papers'
import { HOME_CANVAS, HOME_MOTION, HOME_STEPS, cardRevealStart } from '../config/homeMotion'
import { useResearchStore } from '../stores/research'
import Brand from './BrandMark.vue'
import Icon from './AppIcon.vue'
import HomePaperExplorer from './HomePaperExplorer.vue'
import { PAPER_EXPLORER } from '../config/paperExplorer'

gsap.registerPlugin(ScrollTrigger)
const router = useRouter()
const store = useResearchStore()
const root = ref<HTMLElement>()
const stage = ref<HTMLElement>()
const header = ref<HTMLElement>()
const canvas = ref<HTMLElement>()
const world = ref<HTMLElement>()
const reader = ref<HTMLElement>()
const readerContent = ref<HTMLElement>()
const readerTitle = ref<HTMLElement>()
const selectedId = ref('rag')
const hoveredId = ref<string | null>(null)
const initialMotion = window.innerWidth >= HOME_MOTION.desktopMinWidth && window.innerHeight >= HOME_MOTION.desktopMinHeight && !store.reducedMotion && !matchMedia('(prefers-reduced-motion: reduce)').matches
const animated = ref(initialMotion)
const phase = ref(0)
const availableCards = ref(initialMotion ? 0 : 63)
const readerAvailable = ref(!initialMotion)
const relationsAvailable = ref(!initialMotion)
const explorerReady = ref(false)
const sceneActive = ref(false)
const explorerBleed = ref({ left: 0, right: 0, top: 0, bottom: 0 })
const cards = HOME_CANVAS.papers.map(card => ({ ...card, paper: paperById(card.id)! }))
const selected = computed(() => paperById(selectedId.value)!)
const focusedId = computed(() => hoveredId.value ?? selectedId.value)
const phaseCaption = computed(() => HOME_STEPS[phase.value].caption)
let media: gsap.MatchMedia | undefined
let mainTimeline: gsap.core.Timeline | undefined
let readingTween: gsap.core.Tween | undefined
let disposed = false
let generation = 0
let resizeFrame = 0

function focusEntry() {
  root.value?.querySelector<HTMLAnchorElement>('.header-cta')?.focus({ preventScroll: true })
}

function syncScene(progress: number) {
  if (disposed) return
  const canExplore = progress >= HOME_MOTION.stages.settled
  if (explorerReady.value !== canExplore) explorerReady.value = canExplore
  const nextPhase = progress < HOME_MOTION.stages.papers ? 0 : progress < HOME_MOTION.stages.connections ? 1 : progress < HOME_MOTION.stages.reading ? 2 : 3
  if (phase.value !== nextPhase) phase.value = nextPhase
  const mask = cards.reduce((bits, _, index) => progress >= cardRevealStart(index) + HOME_MOTION.cardRevealDuration * 0.2 ? bits | (1 << index) : bits, 0)
  if (availableCards.value !== mask) {
    const focused = document.activeElement as HTMLElement | null
    const index = cards.findIndex(card => card.id === focused?.dataset.paperId)
    if (index >= 0 && !(mask & (1 << index))) focusEntry()
    availableCards.value = mask
  }
  const canRead = progress >= HOME_MOTION.stages.reading + (HOME_MOTION.stages.settled - HOME_MOTION.stages.reading) * 0.2
  if (readerAvailable.value !== canRead) {
    if (!canRead && reader.value?.contains(document.activeElement)) focusEntry()
    readerAvailable.value = canRead
  }
  const canSeeRelations = progress >= HOME_MOTION.stages.connections
  if (relationsAvailable.value !== canSeeRelations) relationsAvailable.value = canSeeRelations
}

function initializeMotion() {
  if (disposed || !root.value || !stage.value || !canvas.value || !world.value || !reader.value || !header.value) return
  readingTween?.progress(1).kill()
  media?.revert()
  mainTimeline = undefined
  media = gsap.matchMedia()
  const sceneElement = root.value, stageElement = stage.value, canvasElement = canvas.value
  const worldElement = world.value, readerElement = reader.value, headerElement = header.value
  media.add({
    all: '(min-width: 0px)',
    wide: `(min-width: ${HOME_MOTION.desktopMinWidth}px)`,
    tall: `(min-height: ${HOME_MOTION.desktopMinHeight}px)`,
    reduce: '(prefers-reduced-motion: reduce)',
  }, context => {
    const current = ++generation
    const conditions = context.conditions as { wide: boolean; tall: boolean; reduce: boolean }
    const enable = conditions.wide && conditions.tall && !conditions.reduce && !store.reducedMotion
    animated.value = enable
    sceneActive.value = false
    const select = gsap.utils.selector(sceneElement)
    const nodes = Array.from(sceneElement.querySelectorAll<HTMLElement>('.story-paper'))
    const paths = Array.from(sceneElement.querySelectorAll<SVGPathElement>('.story-edge'))
    const placeWorld = context.add('placeWorld', () => {
      if (window.innerWidth < HOME_MOTION.desktopMinWidth) {
        gsap.set(worldElement, { clearProps: 'transform,transformOrigin' })
        return
      }
      const bounds = canvasElement.getBoundingClientRect()
      const asideWidth = readerElement.offsetWidth
      const scale = Math.max(0.1, Math.min((bounds.width - asideWidth - HOME_CANVAS.readerGap) / HOME_CANVAS.width, bounds.height / HOME_CANVAS.height, 1))
      gsap.set(worldElement, {
        x: -(asideWidth + HOME_CANVAS.readerGap) / 2,
        scale,
        transformOrigin: '50% 50%',
      })
      // Expand the clipping viewport into unused space without changing paper scale or pin timing.
      const worldBounds = worldElement.getBoundingClientRect()
      const stageBounds = stageElement.getBoundingClientRect()
      const contextElement = sceneElement.querySelector<HTMLElement>('.story-context')!
      const noteElement = sceneElement.querySelector<HTMLElement>('.story-graph-note')!
      const viewport = PAPER_EXPLORER.viewport
      const contextBottom = stageBounds.top + contextElement.offsetTop + contextElement.offsetHeight
      const noteBottom = Number.parseFloat(getComputedStyle(noteElement).bottom)
      const noteTop = Number.isFinite(noteBottom)
        ? stageBounds.bottom - noteBottom - noteElement.offsetHeight
        : stageBounds.top + noteElement.offsetTop
      const room = (available: number, limit: number) => Math.max(0, Math.min(limit, available / scale))
      explorerBleed.value = {
        left: room(worldBounds.left - stageBounds.left - viewport.stageGutter, viewport.horizontalBleed),
        right: room(bounds.right - asideWidth - viewport.readerClearance - worldBounds.right, viewport.horizontalBleed),
        top: room(worldBounds.top - contextBottom - viewport.verticalClearance, viewport.verticalBleed),
        bottom: room(noteTop - viewport.verticalClearance - worldBounds.bottom, viewport.verticalBleed),
      }
    })

    if (!enable) {
      phase.value = 3
      availableCards.value = 63
      readerAvailable.value = true
      relationsAvailable.value = true
      gsap.set(nodes, { x: 0, y: 0, scale: 1, rotation: 0, autoAlpha: 1 })
      gsap.set(paths, { strokeDashoffset: 0 })
      const layoutStatic = () => {
        cancelAnimationFrame(resizeFrame)
        resizeFrame = requestAnimationFrame(() => { if (!disposed && current === generation) placeWorld() })
      }
      window.addEventListener('resize', layoutStatic)
      nextTick(() => { if (!disposed && current === generation) placeWorld() })
      return () => { generation++; window.removeEventListener('resize', layoutStatic); cancelAnimationFrame(resizeFrame) }
    }

    syncScene(0)
    gsap.set(select('.story-context'), { autoAlpha: 0, y: 20 })
    gsap.set(readerElement, { autoAlpha: 0, x: HOME_MOTION.readerOffset })
    gsap.set(select('.story-graph-note'), { autoAlpha: 0, y: 8 })
    gsap.set(select('.story-canvas-backdrop'), { opacity: 0 })
    gsap.set(paths, { strokeDashoffset: 1 })
    gsap.set(nodes, { y: HOME_MOTION.cardOffset, scale: HOME_MOTION.cardScale, autoAlpha: 0, transformOrigin: '50% 50%' })
    placeWorld()
    const sceneTimeline = gsap.timeline({
      defaults: { ease: 'none' },
      scrollTrigger: {
        id: 'orbit-home-story', trigger: sceneElement, pin: stageElement,
        start: 'top top', end: () => `+=${window.innerHeight * HOME_MOTION.scrollViewports}`,
        scrub: HOME_MOTION.scrubSeconds, pinSpacing: true,
        anticipatePin: HOME_MOTION.anticipatePin, invalidateOnRefresh: true,
        onRefreshInit: () => placeWorld(),
        onRefresh: () => placeWorld(),
        onToggle: self => { if (!disposed) sceneActive.value = self.isActive },
      },
      onUpdate() { syncScene(this.progress()) },
    })
    mainTimeline = sceneTimeline
    const timing = HOME_MOTION.stages
    sceneTimeline
      .addLabel('still', timing.still)
      .addLabel('title', timing.title)
      .addLabel('papers', timing.papers)
      .addLabel('connections', timing.connections)
      .addLabel('reading', timing.reading)
      .addLabel('settled', timing.settled)
      .to(select('.story-intro-copy'), {
        y: () => headerElement.offsetHeight / 2 - stageElement.clientHeight / 2,
        scale: () => HOME_MOTION.titleSecondarySize / Number(getComputedStyle(sceneElement.querySelector('h1')!).fontSize.replace('px', '')),
        duration: timing.titleSettled - timing.title,
      }, 'title')
      .to(select('.story-halo'), { scale: 1.08, yPercent: -3, opacity: 0.5, duration: timing.papers - timing.title }, 'title')
      .to(select('.story-context'), { y: 0, autoAlpha: 1, duration: timing.papers - timing.question }, timing.question)

    nodes.forEach((node, index) => sceneTimeline.to(node, {
      y: 0, scale: 1, autoAlpha: 1, duration: HOME_MOTION.cardRevealDuration,
    }, cardRevealStart(index)))
    sceneTimeline
      .to(select('.story-canvas-backdrop'), { opacity: 1, duration: timing.reading - timing.connections }, 'connections')
      .to(paths, { strokeDashoffset: 0, duration: 0.11, stagger: (timing.reading - timing.connections - 0.11) / Math.max(1, paths.length - 1) }, 'connections')
      .to(select('.story-graph-note'), { autoAlpha: 1, y: 0, duration: 0.05 }, 'connections')
      .to(readerElement, { autoAlpha: 1, x: 0, duration: timing.settled - timing.reading }, 'reading')
      .fromTo(select('.story-progress-fill'), { scaleX: 0 }, { scaleX: 1, duration: timing.end }, 0)
      .addLabel('end', timing.end)
    nextTick(() => { if (!disposed && current === generation) ScrollTrigger.refresh() })
    return () => { generation++ }
  }, sceneElement)
}

function refreshPaperTargets() {
  // A child-only hot reload replaces reveal targets without remounting this scene.
  if (mainTimeline) nextTick(initializeMotion)
}

function seekProgress(progress: number) {
  const trigger = mainTimeline?.scrollTrigger
  if (animated.value && trigger) {
    window.scrollTo({ top: trigger.start + (trigger.end - trigger.start) * progress, behavior: 'instant' })
    ScrollTrigger.update()
    // A step click is a seek, not another scroll animation competing with the scrub tween.
    trigger.getTween()?.progress(1)
    mainTimeline?.progress(progress)
    return
  }
  const target = progress === 0 ? root.value?.querySelector('.story-intro') : progress >= HOME_MOTION.stages.reading ? reader.value : root.value?.querySelector('.story-context')
  target?.scrollIntoView({ block: progress === 0 ? 'center' : 'start', behavior: 'instant' })
}

function seekStep(index: number) {
  const step = HOME_STEPS[index]
  if (!step) return
  seekProgress(step.progress)
  requestAnimationFrame(() => {
    if (index === 0) root.value?.querySelector<HTMLElement>('h1')?.focus({ preventScroll: true })
    else if (index === 3) readerTitle.value?.focus({ preventScroll: true })
    else root.value?.querySelector<HTMLButtonElement>(`[data-paper-id="${index === 1 ? cards[0].id : selectedId.value}"]`)?.focus({ preventScroll: true })
  })
}
defineExpose({ seekStep })

function returnToIntro() { seekProgress(0); requestAnimationFrame(focusEntry) }
async function selectPaper(id: string) {
  selectedId.value = id
  if (window.innerWidth < HOME_MOTION.desktopMinWidth) {
    await nextTick()
    readerTitle.value?.focus({ preventScroll: true })
    reader.value?.scrollIntoView({ block: 'start', behavior: 'instant' })
  }
}
async function askInWorkspace() {
  store.selectedPaperId = null
  await router.push({ path: '/chat', query: { compose: '1' } })
  await nextTick()
  document.querySelector<HTMLTextAreaElement>('#assistant-question')?.focus({ preventScroll: true })
}
function openPaper() { store.uploadOpen=true; router.push('/chat') }

watch(selectedId, async () => {
  await nextTick()
  if (disposed || !readerAvailable.value || !readerContent.value || store.reducedMotion || matchMedia('(prefers-reduced-motion: reduce)').matches) return
  readingTween?.kill()
  readingTween = gsap.fromTo(readerContent.value, { opacity: 0.65, y: 4 }, {
    opacity: 1, y: 0, duration: HOME_MOTION.contentFadeSeconds, ease: 'power1.out', clearProps: 'opacity,transform',
  })
})
watch(() => store.reducedMotion, initializeMotion)
onMounted(() => {
  initializeMotion()
  window.addEventListener('orbit:focus-question', returnToIntro)
  document.fonts.ready.then(() => { if (!disposed) ScrollTrigger.refresh() })
})
onBeforeUnmount(() => {
  disposed = true
  generation++
  readingTween?.kill()
  cancelAnimationFrame(resizeFrame)
  window.removeEventListener('orbit:focus-question', returnToIntro)
  media?.revert()
  mainTimeline = undefined
})
</script>

<template>
  <section ref="root" id="experience" class="story-scroll" :class="{ 'is-static': !animated }" aria-label="从研究问题到论文图谱" :data-phase="phase">
    <div ref="stage" class="story-stage">
      <div class="story-halo" aria-hidden="true"></div>
      <header ref="header" class="story-header">
        <RouterLink to="/" aria-label="研迹 Orbit 首页" @click="seekProgress(0)"><Brand /></RouterLink>
        <RouterLink class="header-cta" to="/chat">进入工作台<Icon name="ArrowUpRight" :size="16" /></RouterLink>
      </header>
      <div class="story-intro"><div class="story-intro-copy"><h1 tabindex="-1">看见<span>研究的脉络</span></h1></div></div>
      <div class="story-context">
        <div class="story-context-label"><span class="story-dot"></span>跟随一个问题，走进研究现场</div>
        <h2 id="experience-title">{{ defaultQuestion }}</h2><p>{{ phaseCaption }}</p>
      </div>
      <div ref="canvas" id="home-papers" class="story-canvas">
        <div class="story-canvas-backdrop" aria-hidden="true"></div>
        <div class="story-world-anchor"><div ref="world" class="story-world">
          <HomePaperExplorer :cards="cards" :selected-id="selectedId" :focused-id="focusedId" :available-cards="availableCards" :animated="animated" :enabled="animated && sceneActive && explorerReady" :bleed="explorerBleed" @select="selectPaper" @hover="hoveredId = $event" @ready="refreshPaperTargets" />
        </div></div>
        <aside ref="reader" class="story-reader" :inert="!readerAvailable" :aria-hidden="!readerAvailable" aria-label="选中论文的阅读提示">
          <div class="story-reader-top"><span><Icon name="Sparkles" :size="16" />AI 阅读提示</span></div>
          <div ref="readerContent" class="story-reader-content">
            <div class="story-reader-paper"><span>{{ selected.tag }} · {{ selected.year||'待添加' }}</span><h3 ref="readerTitle" tabindex="-1">{{ selected.shortTitle }}</h3><p>{{ selected.authors }}</p></div>
            <div class="story-reader-insight"><span class="story-insight-label">先理解这件事</span><p>{{ selected.insight }}</p></div>
          </div>
          <button class="story-source-link" @click="store.uploadOpen=true">上传论文原文<Icon name="Plus" :size="14" /></button>
          <button type="button" class="story-reader-open" @click="openPaper">进入工作台深入阅读<Icon name="ArrowUpRight" :size="16" /></button>
          <button type="button" class="story-ask-again" @click="askInWorkspace">提出我的研究问题<Icon name="ArrowRight" :size="14" /></button>
          <a class="story-back-to-papers" href="#home-papers">返回论文卡片<Icon name="ArrowUp" :size="14" /></a>
        </aside>
      </div>
      <div class="story-graph-note" :aria-hidden="!relationsAvailable"><span><Icon name="MousePointer2" :size="14" />点击论文查看解读 · 滚轮继续浏览</span><span>主题关联，非真实引用关系</span></div>
      <footer class="story-footer">
        <div class="story-scroll-hint"><Icon :name="phase === 3 ? 'Network' : 'MousePointer2'" :size="15" /><span>{{ phase === 3 ? '沿着好奇心，继续探索' : '向下滚动，让线索慢慢展开' }}</span></div>
        <div class="story-stage-progress" aria-label="当前研究阶段"><div class="story-progress-track" aria-hidden="true"><span class="story-progress-fill"></span></div><span>0{{ phase + 1 }} / 04</span><strong>{{ HOME_STEPS[phase].title }}</strong></div>
        <button v-if="animated" type="button" class="story-skip" @click="phase === 3 ? returnToIntro() : seekProgress(HOME_STEPS[3].progress)">{{ phase === 3 ? '回到首页' : '跳过动画' }}<Icon :name="phase === 3 ? 'ArrowUp' : 'ArrowRight'" :size="14" /></button><span v-else class="story-static-label">研究展示 · 自然滚动</span>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.story-scroll { position: relative; }
.story-stage { position: relative; height: 100svh; min-height: 720px; overflow: clip; isolation: isolate; background: var(--landing-bg); }
.story-halo { position: absolute; inset: -15% -8% 0; pointer-events: none; z-index: -1; background: radial-gradient(ellipse at 49% 53%, color-mix(in srgb, var(--purple-soft) 90%, transparent), transparent 61%); }
.story-halo::before, .story-halo::after { content: ''; position: absolute; width: 84%; height: 76%; left: 8%; top: 9%; border: 1px solid color-mix(in srgb, var(--purple) 6%, transparent); border-radius: 50%; transform: rotate(-12deg); }
.story-halo::after { inset: 18% 15% auto; width: 70%; height: 59%; transform: rotate(12deg); }
.story-header { height: 86px; padding: 0 48px; max-width: 1440px; margin: auto; display: flex; align-items: center; justify-content: space-between; position: relative; z-index: 10; }
.story-intro { position: absolute; left: 0; right: 0; top: 50%; transform: translateY(-50%); z-index: 8; text-align: center; pointer-events: none; }
.story-intro-copy { width: fit-content; margin-inline: auto; }
.story-intro h1 { font-size: clamp(52px, 4.8vw, 70px); font-weight: 600; line-height: 1.25; letter-spacing: -.055em; text-wrap: balance; }
.story-intro h1 span { background: linear-gradient(105deg, var(--title-gradient-start), var(--title-gradient-end)); color: var(--purple); background-clip: text; -webkit-text-fill-color: transparent; }
.story-context { position: absolute; top: 109px; left: 48px; right: 48px; z-index: 3; text-align: center; pointer-events: none; }
.story-context-label { display: flex; justify-content: center; align-items: center; gap: 8px; color: var(--muted); font-size: 12px; letter-spacing: .04em; }
.story-dot { width: 5px; height: 5px; background: var(--purple); border-radius: 50%; }
.story-context h2 { font-size: clamp(23px, 2.1vw, 31px); font-weight: 500; letter-spacing: -.03em; margin-top: 9px; }
.story-context > p { font-size: 13px; color: var(--muted); margin-top: 7px; }
.story-canvas { position: absolute; width: calc(100% - 80px); max-width: 1440px; top: 220px; bottom: 106px; left: 50%; transform: translateX(-50%); scroll-margin-top: 24px; }
.story-canvas-backdrop { position: absolute; inset: -5px; background-image: radial-gradient(var(--canvas-dot) .7px, transparent .7px); background-size: 22px 22px; mask-image: radial-gradient(ellipse at 40% 50%, var(--text) 30%, transparent 76%); pointer-events: none; }
.story-world-anchor { position: absolute; left: 50%; top: 50%; width: 900px; height: 510px; transform: translate(-50%, -50%); }
.story-world { position: relative; width: 900px; height: 510px; transform-origin: 50% 50%; }
.story-reader { position: absolute; width: 300px; right: 0; top: 50%; transform: translateY(-50%); border: 1px solid var(--border); border-radius: 16px; padding: 21px; background: var(--glass-surface); box-shadow: var(--shadow-md); scroll-margin-top: 24px; }
.story-reader-top { display: flex; justify-content: space-between; align-items: center; color: var(--purple-deep); font-size: 13px; }
.story-reader-top > span:first-child { display: inline-flex; align-items: center; gap: 8px; }
.story-reader-paper { margin-top: 21px; min-height: 125px; padding-bottom: 17px; border-bottom: 1px solid var(--border); }
.story-reader-paper > span { font-size: 11px; color: var(--muted); }
.story-reader h3 { margin-top: 7px; font-size: 19px; line-height: 1.35; font-weight: 500; letter-spacing: -.025em; overflow-wrap: anywhere; }
.story-reader-paper > p { font-size: 12px; color: var(--muted); margin-top: 8px; }
.story-reader-insight { margin-top: 18px; }
.story-insight-label { font-size: 12px; font-weight: 500; color: var(--purple-deep); }
.story-reader-insight > p { font-size: 14px; line-height: 1.85; min-height: 7.4em; margin-top: 8px; color: var(--muted); }
.story-source-link { display: flex; align-items: center; justify-content: space-between; color: var(--purple-deep); font-size: 13px; padding: 13px 0; margin-top: 6px; }
.story-reader-open { display: flex; align-items: center; justify-content: center; gap: 9px; width: 100%; padding: 11px 9px; border-radius: 8px; background: var(--purple); color: var(--on-accent); font-size: 13px; }
.story-reader-open:hover { background: var(--purple-deep); }
.story-ask-again { display: flex; justify-content: center; align-items: center; gap: 7px; margin: 14px auto 0; font-size: 12px; color: var(--muted); }
.story-ask-again:hover { color: var(--purple-deep); }
.story-back-to-papers { display: none; }
.story-graph-note { position: absolute; bottom: 88px; left: 48px; right: 48px; max-width: 1280px; margin: auto; display: flex; justify-content: space-between; color: var(--muted); font-size: 12px; pointer-events: none; }
.story-graph-note > span:first-child { display: flex; align-items: center; gap: 7px; }
.story-footer { position: absolute; bottom: 23px; left: 48px; right: 48px; display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 28px; z-index: 6; }
.story-scroll-hint { display: flex; align-items: center; gap: 9px; font-size: 12px; color: var(--muted); }
.story-stage-progress { position: relative; display: flex; justify-content: center; gap: 10px; width: 220px; padding-top: 15px; color: var(--muted); font-size: 12px; }
.story-stage-progress > span { font-size: 11px; font-variant-numeric: tabular-nums; }
.story-stage-progress > strong { font-weight: 500; color: var(--purple-deep); }
.story-progress-track { height: 1px; background: var(--border-light); position: absolute; top: 0; left: 0; right: 0; overflow: hidden; }
.story-progress-fill { display: block; width: 100%; height: 100%; background: var(--purple); transform-origin: left; transform: scaleX(0); }
.story-skip { display: flex; align-items: center; justify-self: end; gap: 7px; font-size: 12px; color: var(--muted); min-height: 30px; }
.story-skip:hover { color: var(--purple-deep); }
.story-static-label { justify-self: end; color: var(--muted); font-size: 12px; }
.story-scroll :is(button, a, h1, h3):focus-visible { outline: 2px solid var(--focus); outline-offset: 4px; }
.story-scroll.is-static .story-stage { height: auto; min-height: 0; padding-bottom: 24px; }
.is-static .story-intro { position: relative; top: auto; transform: none; min-height: calc(100svh - 86px); display: grid; place-items: center; padding: 60px 0; }
.is-static .story-context { position: relative; inset: auto; padding-top: 30px; }
.is-static #experience-title { scroll-margin-top: 28px; }
.is-static .story-canvas { position: relative; top: auto; bottom: auto; height: 560px; margin-top: 30px; }
.is-static .story-graph-note { position: relative; inset: auto; width: calc(100% - 96px); margin: 5px auto 34px; }
.is-static .story-footer { position: relative; inset: auto; margin: 0 48px; }
.is-static .story-progress-fill { transform: scaleX(1); }
@media (max-height: 800px) and (min-height: 720px) {
  .story-intro h1 { font-size: clamp(48px, 4.5vw, 61px); }
  .story-context { top: 99px; }
  .story-canvas { top: 199px; bottom: 96px; }
  .story-reader { padding: 17px; top: calc(50% - 22px); }
  .story-reader-paper { margin-top: 13px; padding-bottom: 12px; min-height: 95px; }
  .story-reader-insight { margin-top: 12px; }
  .story-reader-insight > p { font-size: 13px; line-height: 1.75; min-height: 7em; }
  .story-ask-again { margin-top: 10px; }
  .story-graph-note { bottom: 80px; }
  .story-footer { bottom: 18px; }
}
@media (max-width: 1199px) {
  .story-header { padding-inline: 32px; }
  .story-canvas { width: calc(100% - 64px); }
  .story-reader { width: 265px; padding: 17px; }
  .story-reader h3 { font-size: 17px; }
  .story-footer { left: 32px; right: 32px; gap: 16px; }
  .story-scroll-hint { max-width: 160px; font-size: 11px; }
  .story-graph-note { left: 32px; right: 32px; }
}
@media (max-width: 1023px) {
  .story-header { height: 80px; padding-inline: 24px; }
  .story-intro h1 { font-size: clamp(36px, 6vw, 52px); }
  .is-static .story-intro { min-height: min(76svh, 640px); padding: 50px 20px; }
  .is-static .story-context { padding: 28px 24px 0; }
  .story-context-label { font-size: 12px; flex-wrap: wrap; }
  .story-context h2 { font-size: clamp(22px, 3.2vw, 30px); line-height: 1.5; }
  .story-context > p { line-height: 1.8; }
  .is-static .story-canvas { width: calc(100% - 48px); height: auto; margin-top: 30px; }
  .story-canvas-backdrop { display: none; }
  .story-world-anchor { position: static; width: auto; height: auto; transform: none; }
  .story-world { width: auto; height: auto; }
  .story-reader { position: relative; top: auto; right: auto; transform: none; width: 100%; margin-top: 24px; padding: 24px; }
  .story-reader-paper { min-height: 0; }
  .story-reader-insight > p { min-height: 0; }
  .story-reader-open { min-height: 44px; }
  .story-ask-again { min-height: 36px; }
  .story-back-to-papers { display: flex; justify-content: center; align-items: center; gap: 7px; min-height: 36px; margin-top: 6px; font-size: 12px; color: var(--muted); }
  .is-static .story-graph-note { width: calc(100% - 48px); display: grid; gap: 10px; margin-block: 22px 24px; font-size: 12px; line-height: 1.7; }
  .is-static .story-footer { margin-inline: 24px; grid-template-columns: 1fr auto; gap: 12px; }
  .story-stage-progress { display: none; }
  .story-scroll-hint { max-width: none; }
}
@media (max-width: 560px) {
  .story-header :deep(.brand-chinese) { display: none; }
  .story-header :deep(.brand-name) { font-size: 23px; }
  .story-header .header-cta { font-size: 13px; min-height: 40px; }
  .story-static-label { font-size: 11px; }
}
</style>
