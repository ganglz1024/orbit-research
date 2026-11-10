<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Icon from './AppIcon.vue'

const props = defineProps<{ modelValue: string; options: ReadonlyArray<{ value: string; label: string; icon: string }>; label: string; idPrefix: string; panelId: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const root = ref<HTMLElement>(), clip = ref('inset(0 100% 0 0 round 5px)'), ready = ref(false)
let observer: ResizeObserver | undefined, frame = 0, disposed = false
function measure() {
  if (disposed || !root.value) return
  const current = root.value.querySelector<HTMLButtonElement>('[aria-selected="true"]')
  if (!current) return
  const left = Math.max(0, current.offsetLeft - 3)
  const right = Math.max(0, root.value.clientWidth - 6 - left - current.offsetWidth)
  clip.value = `inset(0 ${right}px 0 ${left}px round 5px)`
  cancelAnimationFrame(frame); frame = requestAnimationFrame(() => { ready.value = true })
}
function key(event: KeyboardEvent, index: number) {
  let next: number
  if (event.key === 'ArrowRight') next = (index + 1) % props.options.length
  else if (event.key === 'ArrowLeft') next = (index + props.options.length - 1) % props.options.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = props.options.length - 1
  else return
  event.preventDefault()
  emit('update:modelValue', props.options[next]!.value)
  nextTick(() => root.value?.querySelectorAll<HTMLButtonElement>('button')[next]?.focus({ preventScroll: true }))
}
watch(() => props.modelValue, () => nextTick(measure))
onMounted(() => {
  measure(); observer = new ResizeObserver(measure); if (root.value) observer.observe(root.value)
  document.fonts.ready.then(() => { if (!disposed) measure() })
})
onBeforeUnmount(() => { disposed = true; observer?.disconnect(); cancelAnimationFrame(frame) })
</script>

<template>
  <div ref="root" class="segmented-control animated-segmented" :class="{ 'indicator-ready': ready }" role="tablist" :aria-label="label">
    <span class="segmented-indicator" aria-hidden="true" :style="{ clipPath: clip }"></span>
    <button v-for="(option, index) in options" :id="`${idPrefix}-${option.value}`" :key="option.value" type="button" role="tab" :class="{ active: modelValue === option.value }" :aria-selected="modelValue === option.value" :aria-controls="panelId" :tabindex="modelValue === option.value ? 0 : -1" @click="emit('update:modelValue', option.value)" @keydown="key($event, index)"><Icon :name="option.icon" :size="15" />{{ option.label }}</button>
  </div>
</template>
