<script setup lang="ts">
import mark from '../assets/brand/orbit-mark.svg'
defineProps<{compact?:boolean}>()
</script>
<template><span class="brand"><img class="brand-mark" :src="mark" width="35" height="35" alt="" aria-hidden="true"/><span v-if="!compact" class="brand-copy"><span class="brand-name">研迹 Orbit</span><span class="brand-tagline">看见研究的脉络</span></span></span></template>
<style scoped>
.brand-mark { display: block; object-fit: contain; }
.brand-copy { display: grid; gap: 2px; }
.brand .brand-name { letter-spacing: -.025em; white-space: nowrap; line-height: 1.2; }
.brand-tagline { color: var(--muted); font-size: 10px; line-height: 1.4; letter-spacing: .08em; white-space: nowrap; }
</style>
