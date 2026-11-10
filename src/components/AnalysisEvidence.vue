<script setup lang="ts">
import type { Evidence,AnalysisSource } from '../types/copilot'
import { backendApi } from '../services/backendApi'
defineProps<{items:Evidence[];sources:AnalysisSource[]}>()
</script>
<template><div class="analysis-evidence"><details v-for="(e,i) in items" :key="i"><summary><span class="evidence-kind">{{e.kind==='original_support'?'原文片段支持':e.kind==='provided_metadata'?'提供的元数据':'待核验来源'}}</span> {{sources.find(s=>s.id===e.sourceId)?.title}}<span v-if="e.page"> · PDF 第 {{e.page}} 页</span></summary><blockquote>{{e.quote||'未提供可定位原文。'}}</blockquote><p>AI 解释：{{e.explanation}}</p><small>片段匹配不等于结论已被证实，请核对论证条件。</small><a v-if="e.kind==='original_support'&&sources.find(s=>s.id===e.sourceId)?.documentId&&e.page" :href="backendApi.contentUrl(sources.find(s=>s.id===e.sourceId)!.documentId!,sources.find(s=>s.id===e.sourceId)!.versionId)+'#page='+e.page" target="_blank" rel="noopener noreferrer">打开 PDF 第 {{e.page}} 页 ↗</a></details></div></template>
