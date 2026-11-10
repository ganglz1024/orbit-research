<script setup lang="ts">
import {computed} from 'vue'
import {renderResearchMarkdown} from '../lib/researchMarkdown'
import 'katex/dist/katex.min.css'
const props=defineProps<{text:string}>(),html=computed(()=>renderResearchMarkdown(props.text))
</script>
<template><div class="research-markdown" v-html="html"></div></template>
