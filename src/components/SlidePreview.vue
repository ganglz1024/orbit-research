<script setup lang="ts">
import type { Slide } from '../types'
defineProps<{slide:Slide;index:number;thumbnail?:boolean}>()
</script>
<template><div class="slide-preview" :class="[slide.type,{'is-thumbnail':thumbnail}]"><span class="slide-brand">ORBIT / RESEARCH NOTES</span><div v-if="slide.type==='cover'" class="slide-orbit" aria-hidden="true"><i></i><i></i><i></i></div><h2>{{slide.title}}</h2><p>{{slide.body}}</p><footer><span>阅读草稿</span><span>{{String(index+1).padStart(2,'0')}}</span></footer></div></template>
