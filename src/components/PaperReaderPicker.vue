<script setup lang="ts">
import {computed,nextTick,onMounted,onBeforeUnmount,ref} from 'vue'
import {useResearchStore} from '../stores/research'
import {readerUrl} from '../lib/paperSources'
import Icon from './AppIcon.vue'
const emit=defineEmits<{close:[]}>(),store=useResearchStore(),panel=ref<HTMLElement>()
const papers=computed(()=>store.activePapers.filter(p=>p.localDocumentId))
let previous:HTMLElement|null=null
function keys(e:KeyboardEvent){if(e.key==='Escape'){e.preventDefault();e.stopPropagation();emit('close')}if(e.key==='Tab'){const items=[...panel.value!.querySelectorAll<HTMLElement>('a[href],button:not([disabled])')];if(e.shiftKey&&document.activeElement===items[0]){e.preventDefault();items.at(-1)?.focus()}else if(!e.shiftKey&&document.activeElement===items.at(-1)){e.preventDefault();items[0]?.focus()}}}
onMounted(()=>{previous=document.activeElement as HTMLElement;void nextTick(()=>panel.value?.querySelector('button')?.focus());document.addEventListener('keydown',keys,true)})
onBeforeUnmount(()=>{document.removeEventListener('keydown',keys,true);if(previous?.isConnected)previous.focus()})
</script>
<template><Teleport to="body"><div class="modal-scrim" @click.self="emit('close')"><section ref="panel" class="upload-dialog paper-reader-picker" role="dialog" aria-modal="true" aria-labelledby="paper-reader-title"><header><div><h2 id="paper-reader-title">选择一篇论文开始阅读</h2><p class="settings-hint">打开 PDF，搜索原文、划线批注并继续上次阅读。</p></div><button class="icon-button" aria-label="关闭论文阅读选择" @click="emit('close')"><Icon name="X"/></button></header><nav aria-label="当前空间可阅读的论文"><RouterLink v-for="paper in papers" :key="paper.id" class="reader-picker-item" :to="readerUrl(paper.localDocumentId!,store.activeId||'default')" @click="emit('close')"><Icon name="BookOpen" :size="20"/><span><strong>{{paper.title}}</strong><small>{{paper.authors||'作者未提供'}} · {{paper.year||'年份未提供'}}</small></span><Icon name="ArrowRight" :size="16"/></RouterLink></nav><p v-if="!papers.length">当前空间还没有绑定 PDF 的论文，请先上传论文 PDF。</p><button class="secondary-button" @click="emit('close');store.uploadOpen=true"><Icon name="Plus" :size="15"/>上传论文 PDF</button></section></div></Teleport></template>
<style scoped>.paper-reader-picker{max-width:640px}.paper-reader-picker nav{max-height:50dvh;overflow:auto;margin:16px 0}.reader-picker-item{display:flex;align-items:center;gap:12px;padding:14px 10px;border-radius:10px;color:var(--text);text-decoration:none;transition:background 160ms}.reader-picker-item:hover{background:var(--purple-soft)}.reader-picker-item>span{flex:1;min-width:0}.reader-picker-item strong{display:block;font-size:14px;line-height:1.6;overflow-wrap:anywhere}.reader-picker-item small{display:block;color:var(--muted);font-size:12px;margin-top:4px}</style>
