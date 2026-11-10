<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Paper } from '../types'
import { HOME_CANVAS } from '../config/homeMotion'
import { PAPER_COVERS, PAPER_EXPLORER as SETTINGS } from '../config/paperExplorer'
import Icon from './AppIcon.vue'
import { PaperPanMotion } from '../lib/paperPanMotion'

const props = defineProps<{
  cards: Array<{ id: string; x: number; y: number; paper: Paper }>
  selectedId: string
  focusedId: string
  availableCards: number
  animated: boolean
  enabled: boolean
  bleed: { left: number; right: number; top: number; bottom: number }
}>()
const emit = defineEmits<{ select: [id: string]; hover: [id: string | null]; ready: [] }>()
const viewport = ref<HTMLElement>()
const panLayer = ref<HTMLElement>()
const dragging = ref(false)
const failedImages = ref(new Set<string>())
const edges = computed(() => HOME_CANVAS.connections.filter(([a, b]) => props.cards.some(card => card.id === a) && props.cards.some(card => card.id === b)))
const related = computed(() => new Set<string>(edges.value.filter(([a, b]) => a === props.focusedId || b === props.focusedId).flat()))
const motion = new PaperPanMotion()
let pointer: number | undefined
let frame = 0
let boundsFrame = 0
let disposed = false
let suppressClickUntil = 0

function paint() {
  panLayer.value?.style.setProperty('--pan-x', `${motion.x.toFixed(2)}px`)
  panLayer.value?.style.setProperty('--pan-y', `${motion.y.toFixed(2)}px`)
}
function stop() { cancelAnimationFrame(frame); frame = 0; motion.cancel() }
function cancelGesture() {
  const id = pointer
  pointer = undefined
  if (dragging.value) suppressClickUntil = performance.now() + 300
  dragging.value = false
  if (id !== undefined && viewport.value?.hasPointerCapture(id)) viewport.value.releasePointerCapture(id)
  stop()
}
function releaseInertia() {
  let last = performance.now()
  const tick = (time: number) => {
    if (!props.enabled) { stop(); return }
    const active = motion.step(time - last)
    last = time
    paint()
    if (!active) { frame = 0; return }
    frame = requestAnimationFrame(tick)
  }
  frame = requestAnimationFrame(tick)
}
function pointerDown(event: PointerEvent) {
  if (event.pointerType !== 'mouse' || event.button !== 0 || (event.target as HTMLElement).closest('.paper-controls')) return
  const target = (event.target as HTMLElement).closest<HTMLButtonElement>('.paper-face')
  if (target) { event.preventDefault(); target.focus({ preventScroll: true }) }
  if (!props.enabled) return
  stop()
  suppressClickUntil = 0
  pointer = event.pointerId
  motion.begin(event.clientX, event.clientY, event.timeStamp, viewport.value!.getBoundingClientRect().width / viewport.value!.offsetWidth)
}
function pointerMove(event: PointerEvent) {
  if (pointer === undefined || event.pointerId !== pointer) return
  if (!motion.move(event.clientX, event.clientY, event.timeStamp)) return
  if (!dragging.value) { dragging.value = true; viewport.value?.setPointerCapture(event.pointerId) }
  event.preventDefault()
  paint()
}
function pointerUp(event: PointerEvent) {
  if (pointer === undefined || pointer !== event.pointerId) return
  const moved = motion.end(event.timeStamp)
  pointer = undefined
  dragging.value = false
  if (moved) {
    suppressClickUntil = performance.now() + 300
    event.preventDefault()
  }
  if (viewport.value?.hasPointerCapture(event.pointerId)) viewport.value.releasePointerCapture(event.pointerId)
  if (moved) releaseInertia()
}
function guardClick(event: MouseEvent) {
  if (event.detail > 0 && performance.now() < suppressClickUntil) { event.preventDefault(); event.stopPropagation() }
}
function move(dx: number, dy: number) {
  if (!props.enabled) return
  cancelGesture()
  motion.nudge(dx, dy)
  paint()
}
function keyPan(event: KeyboardEvent) {
  if (!props.enabled || event.altKey || event.ctrlKey || event.metaKey) return
  const shifts: Record<string, [number, number]> = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }
  const shift = shifts[event.key]
  if (shift) { event.preventDefault(); move(shift[0] * SETTINGS.keyboardStep, shift[1] * SETTINGS.keyboardStep) }
}
function reset() { cancelGesture(); motion.reset(); paint() }
function measureVerticalRoom() {
  if (disposed || pointer !== undefined || !props.enabled || !viewport.value || !panLayer.value) return
  const bounds = viewport.value.getBoundingClientRect()
  const scale = bounds.width / viewport.value.offsetWidth
  const inset = (SETTINGS.viewport.verticalFeather + SETTINGS.viewport.projectionGuard) * scale
  const response = scale * SETTINGS.viewport.verticalPanResponse
  // Measure the neutral layout once, outside the pointer/inertia loop. Restore before paint.
  panLayer.value.style.setProperty('--pan-y', '0px')
  const papers = Array.from(panLayer.value.querySelectorAll<HTMLElement>('.paper-face'), paper => paper.getBoundingClientRect())
  paint()
  if (!papers.length) return
  const up = Math.max(0, Math.min(...papers.map(paper => (paper.top - bounds.top - inset) / response)))
  const down = Math.max(0, Math.min(...papers.map(paper => (bounds.bottom - inset - paper.bottom) / response)))
  motion.setVerticalBounds(-up, down)
  paint()
}
function queueBounds() { cancelAnimationFrame(boundsFrame); boundsFrame = requestAnimationFrame(measureVerticalRoom) }
function resized() { cancelGesture(); queueBounds() }
function depthChanged(event: TransitionEvent) {
  if (event.propertyName === 'transform' && (event.target as Element).matches('.paper-face')) queueBounds()
}
function paperFocused(id: string, event: FocusEvent) {
  emit('hover', id)
  const target = event.currentTarget as HTMLElement
  if (!props.enabled || !target.matches(':focus-visible') || !viewport.value) return
  const bounds = viewport.value.getBoundingClientRect(), paper = target.getBoundingClientRect()
  const scale = bounds.width / viewport.value.offsetWidth
  const inset = (SETTINGS.viewport.edgeFeather + 8) * scale
  const dx = paper.left < bounds.left + inset ? bounds.left + inset - paper.left : paper.right > bounds.right - inset ? bounds.right - inset - paper.right : 0
  const dy = paper.top < bounds.top + inset ? bounds.top + inset - paper.top : paper.bottom > bounds.bottom - inset ? bounds.bottom - inset - paper.bottom : 0
  if (dx || dy) move(dx / scale, dy / scale)
}
function imageFailed(id: string) { failedImages.value = new Set([...failedImages.value, id]) }
function edgePath(a: string, b: string) {
  const from = props.cards.find(card => card.id === a)!, to = props.cards.find(card => card.id === b)!
  const x1 = from.x + HOME_CANVAS.cardWidth / 2, y1 = from.y + HOME_CANVAS.cardHeight / 2
  const x2 = to.x + HOME_CANVAS.cardWidth / 2, y2 = to.y + HOME_CANVAS.cardHeight / 2
  const mid = (x1 + x2) / 2
  return Math.abs(x1 - x2) < 20 ? `M ${x1} ${y1} C ${x1 + 80} ${y1}, ${x2 + 80} ${y2}, ${x2} ${y2}` : `M ${x1} ${y1} C ${mid} ${y1}, ${mid} ${y2}, ${x2} ${y2}`
}
function cardStyle(index: number) {
  const layer = SETTINGS.layers[index % SETTINGS.layers.length]
  return { left: `${props.cards[index].x}px`, top: `${props.cards[index].y}px`, '--depth': `${layer.z}px`, '--rx': `${layer.rx}deg`, '--ry': `${layer.ry}deg`, '--rz': `${layer.rz}deg`, '--parallax': layer.z / 56 * SETTINGS.parallax }
}
function visibilityChanged() { if (document.hidden) cancelGesture() }
watch(() => props.enabled, enabled => { if (!enabled) cancelGesture() })
watch(() => [props.enabled, props.selectedId, props.bleed], queueBounds, { flush: 'post' })
onMounted(() => { document.addEventListener('visibilitychange', visibilityChanged); window.addEventListener('resize', resized); emit('ready'); queueBounds() })
onBeforeUnmount(() => { disposed = true; cancelGesture(); cancelAnimationFrame(boundsFrame); document.removeEventListener('visibilitychange', visibilityChanged); window.removeEventListener('resize', resized) })
</script>

<template>
  <div ref="viewport" class="paper-explorer" :class="{ 'can-drag': enabled, 'is-dragging': dragging, 'has-scroll-motion': animated, 'is-static-space': !animated }" :style="{ '--paper-perspective': `${SETTINGS.perspective}px`, '--selected-lift': `${SETTINGS.selectedLift}px`, '--bleed-left': `${bleed.left}px`, '--bleed-right': `${bleed.right}px`, '--bleed-top': `${bleed.top}px`, '--bleed-bottom': `${bleed.bottom}px`, '--edge-feather': `${SETTINGS.viewport.edgeFeather}px`, '--vertical-feather': `${SETTINGS.viewport.verticalFeather}px` }" aria-label="论文空间，滚轮浏览页面，稳定展示后可拖动或使用方向按钮平移" @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="cancelGesture" @lostpointercapture="pointer !== undefined && cancelGesture()" @click.capture="guardClick" @keydown="keyPan" @transitionend="depthChanged">
    <div class="paper-clip">
    <div ref="panLayer" class="paper-pan">
      <svg class="story-edges" viewBox="0 0 900 510" aria-hidden="true"><path v-for="[a, b] in edges" :key="`${a}-${b}`" class="story-edge" :class="{ 'is-connected': a === focusedId || b === focusedId }" :d="edgePath(a, b)" pathLength="1" /></svg>
      <div v-for="(card, index) in cards" :key="card.id" class="story-paper" :style="cardStyle(index)" :inert="!(availableCards & (1 << index))" :aria-hidden="!(availableCards & (1 << index))">
        <div class="paper-depth" :class="{ 'is-selected': selectedId === card.id, 'is-related': related.has(card.id) }">
          <button type="button" class="paper-face" :aria-pressed="selectedId === card.id" :aria-label="`查看 ${card.paper.shortTitle} 的解读`" :data-paper-id="card.id" @click="emit('select', card.id)" @mouseenter="emit('hover', card.id)" @mouseleave="emit('hover', null)" @focus="paperFocused(card.id, $event)" @blur="emit('hover', null)">
            <span v-if="selectedId === card.id" class="paper-selected-indicator" aria-hidden="true"><Icon name="Check" :size="11" />已选</span>
            <span class="paper-preview" aria-hidden="true"><img v-if="card.paper.arxiv&&!failedImages.has(card.id)" :src="PAPER_COVERS[card.id]" alt="" width="640" height="829" decoding="async" :draggable="false" @error="imageFailed(card.id)"><span v-else class="paper-image-fallback"><Icon name="FileText" :size="26" /><strong>{{ card.paper.shortTitle }}</strong><span>添加自己的论文后开始阅读</span></span><span class="paper-preview-badge">你的论文</span></span>
            <span class="paper-caption"><span class="paper-meta"><span>{{ card.paper.tag }}</span><span>{{ card.paper.year||'待添加' }}<Icon name="ArrowUpRight" :size="11" /></span></span><strong>{{ card.paper.shortTitle }}</strong><span class="paper-mobile-author">{{ card.paper.authors }} · arXiv {{ card.paper.arxiv }}</span></span>
          </button>
        </div>
      </div>
    </div>
    </div>
    <div class="paper-controls" :class="{ 'is-available': enabled }" :inert="!enabled" :aria-hidden="!enabled">
      <span><Icon name="Move" :size="13" />{{ dragging ? '正在移动' : '拖动探索' }}</span>
      <div role="group" aria-label="平移论文空间"><button type="button" aria-label="向左平移论文空间" @click="move(-SETTINGS.keyboardStep, 0)"><Icon name="ArrowLeft" :size="14" /></button><button type="button" aria-label="向上平移论文空间" @click="move(0, -SETTINGS.keyboardStep)"><Icon name="ArrowUp" :size="14" /></button><button type="button" aria-label="居中论文空间" @click="reset"><Icon name="Focus" :size="15" /></button><button type="button" aria-label="向下平移论文空间" @click="move(0, SETTINGS.keyboardStep)"><Icon class="point-down" name="ArrowUp" :size="14" /></button><button type="button" aria-label="向右平移论文空间" @click="move(SETTINGS.keyboardStep, 0)"><Icon name="ArrowRight" :size="14" /></button></div>
    </div>
  </div>
</template>

<style scoped>
.paper-explorer { position: relative; left: calc(-1 * var(--bleed-left, 0px)); top: calc(-1 * var(--bleed-top, 0px)); width: calc(900px + var(--bleed-left, 0px) + var(--bleed-right, 0px)); height: calc(510px + var(--bleed-top, 0px) + var(--bleed-bottom, 0px)); user-select: none; }
.paper-clip { position: absolute; inset: 0; perspective: var(--paper-perspective); perspective-origin: calc(450px + var(--bleed-left, 0px)) calc(255px + var(--bleed-top, 0px)); overflow: clip; mask-image: linear-gradient(to right, transparent, #000 var(--edge-feather), #000 calc(100% - var(--edge-feather)), transparent), linear-gradient(to bottom, transparent, #000 var(--vertical-feather), #000 calc(100% - var(--vertical-feather)), transparent); mask-composite: intersect; }
.paper-explorer.can-drag, .can-drag .paper-face { cursor: grab; }
.paper-explorer.is-dragging, .is-dragging .paper-face { cursor: grabbing; }
.paper-pan { position: absolute; left: var(--bleed-left, 0px); top: var(--bleed-top, 0px); width: 900px; height: 510px; transform: translate3d(var(--pan-x, 0px), var(--pan-y, 0px), 0); transform-style: preserve-3d; pointer-events: none; }
.story-edges { position: absolute; inset: 0; width: 900px; height: 510px; overflow: visible; pointer-events: none; transform: translateZ(-75px); }
.story-edge { fill: none; stroke: var(--graph-line); stroke-width: 1.3px; stroke-dasharray: 1; stroke-linecap: round; opacity: .4; transition: stroke 180ms, opacity 180ms; }
.story-edge.is-connected { stroke: var(--graph-line-active); stroke-width: 1.7px; opacity: .75; }
.story-paper { position: absolute; width: 196px; height: 210px; transform-style: preserve-3d; pointer-events: none; }
.has-scroll-motion .story-paper { opacity: 0; visibility: hidden; }
.paper-depth { width: 100%; height: 100%; transform-style: preserve-3d; transform: translate3d(calc(var(--pan-x, 0px) * var(--parallax)), calc(var(--pan-y, 0px) * var(--parallax)), var(--depth)) rotateX(var(--rx)) rotateY(var(--ry)) rotateZ(var(--rz)); }
.paper-face { position: relative; width: 100%; height: 100%; padding: 0; display: flex; flex-direction: column; text-align: left; border: 1px solid var(--border); border-radius: 5px; background: var(--card-bg); color: var(--text); box-shadow: 2px 3px 0 color-mix(in srgb, var(--border) 60%, var(--card-bg)), 4px 7px 0 -3px var(--border), 0 12px 22px color-mix(in srgb, var(--shadow-color) 11%, transparent); transform: translateZ(0); transition: transform 220ms, border-color 180ms; pointer-events: auto; }
.paper-depth.is-selected .paper-face { border-color: var(--purple); transform: translateZ(var(--selected-lift)); }
.paper-depth.is-related:not(.is-selected) .paper-face { border-color: color-mix(in srgb, var(--purple) 40%, var(--border)); }
.paper-face:hover { border-color: var(--purple); }
.paper-face:focus-visible, .paper-controls button:focus-visible { outline: 2px solid var(--focus); outline-offset: 4px; }
.paper-preview { position: relative; display: block; height: 141px; width: 100%; border-radius: 4px 4px 0 0; overflow: hidden; background: white; border-bottom: 1px solid var(--border-light); }
.paper-preview img { width: 100%; height: auto; display: block; pointer-events: none; }
.paper-preview-badge { position: absolute; bottom: 7px; right: 7px; padding: 2px 5px; border-radius: 3px; font-size: 9px; color: var(--purple-deep); background: var(--card-bg); border: 1px solid var(--border-light); }
.paper-selected-indicator { position: absolute; top: 7px; right: 7px; z-index: 1; display: inline-flex; align-items: center; gap: 3px; border-radius: 4px; padding: 2px 5px; font-size: 10px; background: var(--purple); color: var(--on-accent); pointer-events: none; }
.paper-caption { padding: 8px 11px 9px; flex: 1; display: flex; flex-direction: column; }
.paper-meta { display: flex; justify-content: space-between; gap: 6px; font-size: 10px; color: var(--muted); }
.paper-meta > span:first-child { color: var(--purple-deep); }
.paper-meta > span:last-child { display: inline-flex; align-items: center; gap: 3px; }
.paper-caption > strong { margin-top: 3px; font-size: 13px; line-height: 1.3; font-weight: 500; letter-spacing: -.025em; }
.paper-mobile-author { display: none; }
.paper-image-fallback { height: 100%; padding: 15px; display: flex; flex-direction: column; justify-content: center; gap: 7px; background: var(--purple-wash); color: var(--purple-deep); }
.paper-image-fallback strong { font-size: 13px; line-height: 1.4; }
.paper-image-fallback > span { font-size: 10px; color: var(--muted); }
.paper-controls { position: absolute; bottom: 4px; left: calc(450px + var(--bleed-left, 0px)); transform: translateX(-50%); display: flex; align-items: center; gap: 10px; padding: 3px 6px 3px 11px; border: 1px solid var(--border); border-radius: 24px; background: var(--card-bg); color: var(--muted); opacity: 0; visibility: hidden; pointer-events: none; transition: opacity 180ms; }
.paper-controls.is-available { opacity: 1; visibility: visible; pointer-events: auto; }
.paper-controls > span { display: inline-flex; align-items: center; gap: 6px; font-size: 11px; white-space: nowrap; }
.paper-controls > div { display: flex; gap: 2px; }
.paper-controls button { width: 44px; height: 44px; display: grid; place-items: center; border-radius: 50%; cursor: pointer; }
.paper-controls button:hover { background: var(--purple-soft); color: var(--purple-deep); }
.point-down { transform: rotate(180deg); }
.is-static-space .paper-face { transition: none; }
.is-static-space .paper-depth, .is-static-space .paper-depth.is-selected .paper-face { transform: none; }
@media (prefers-reduced-motion: reduce) { .paper-face, .story-edge, .paper-controls { transition: none; } .paper-depth { transform: none; } .paper-depth.is-selected .paper-face { transform: none; } }
@media (max-width: 1023px) {
  .paper-explorer { left: auto; top: auto; width: auto; height: auto; user-select: auto; }
  .paper-clip { position: relative; perspective: none; overflow: visible; mask-image: none; }
  .paper-pan { position: relative; left: auto; top: auto; width: auto; height: auto; transform: none; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
  .story-paper { position: relative; left: auto !important; top: auto !important; width: 100%; height: auto; min-height: 164px; }
  .paper-depth { transform: none; }
  .paper-face, .paper-depth.is-selected .paper-face { min-height: 164px; height: 100%; transform: none; border-radius: 12px; cursor: pointer; padding: 14px 15px; box-shadow: var(--shadow-sm); }
  .paper-preview, .story-edges, .paper-controls { display: none; }
  .paper-selected-indicator { display: none; }
  .paper-caption { padding: 0; }
  .paper-meta { font-size: 12px; }
  .paper-caption > strong { margin-top: 12px; font-size: 17px; line-height: 1.35; }
  .paper-mobile-author { display: block; margin-top: auto; padding-top: 13px; font-size: 12px; line-height: 1.6; color: var(--muted); }
  .paper-depth.is-selected .paper-face { background: var(--purple-wash); }
}
@media (max-width: 560px) { .paper-pan { grid-template-columns: minmax(0, 1fr); gap: 14px; } }
</style>
