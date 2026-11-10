<script setup lang="ts">
import {ref} from 'vue'
import type {ReadingRequest,EvidenceBlock} from '../lib/readerContext'
import Icon from './AppIcon.vue'
const props=defineProps<{source:ReadingRequest;block:EvidenceBlock;title:string;removable?:boolean}>()
const emit=defineEmits<{locate:[source:ReadingRequest];remove:[]}>()
const expanded=ref(false)
</script>
<template><article class="pdf-citation-card" aria-label="已引用论文原文">
 <header><Icon name="FileText" :size="14"/><strong>已引用论文原文</strong><button v-if="removable" type="button" class="icon-button citation-remove" aria-label="移除原文引用" @click="emit('remove')"><Icon name="X" :size="13"/></button></header>
 <p class="citation-title"><span class="citation-paper" :title="title">{{title}}</span><span class="citation-page">PDF 第 {{block.pageIndex+1}} 页</span></p>
 <blockquote :class="{expanded}">{{block.quote}}</blockquote>
 <footer><button type="button" class="text-button" :aria-expanded="expanded" @click="expanded=!expanded">{{expanded?'收起原文':'查看完整原文'}}</button><button type="button" class="text-button" @click="emit('locate',{...props.source,evidence:[props.block]})">定位 PDF</button><small>{{block.quote.length}} 字</small></footer>
</article></template>
<style scoped>
.pdf-citation-card{min-width:0;border:1px solid var(--border);border-radius:10px;background:var(--surface);padding:7px 9px;text-align:left;font-weight:400;color:var(--text);font-size:12px;line-height:1.5}
header{display:flex;align-items:center;gap:6px;color:var(--purple)}header strong{font-size:11px;font-weight:500}.citation-remove{margin-left:auto;width:24px;height:24px;min-width:24px;padding:3px}.citation-title{display:flex;gap:6px;font-size:11px;color:var(--muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin:2px 0 4px}
.citation-paper{min-width:0;overflow:hidden;text-overflow:ellipsis}.citation-page{flex:none}
blockquote{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;font-size:12px;line-height:1.55}blockquote.expanded{display:block;max-height:min(180px,20dvh);overflow:auto;overscroll-behavior:contain}
footer{display:flex;align-items:center;gap:12px;margin-top:4px}footer button{font-size:11px}footer small{margin-left:auto;font-size:10px;color:var(--muted)}
</style>
