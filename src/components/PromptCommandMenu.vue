<script setup lang="ts">
import {ref,watch,nextTick} from 'vue'
import type {PromptEntry} from '../lib/promptCommands'
const props=defineProps<{entries:PromptEntry[];active:number;kind:'slash'|'mention'}>()
defineEmits<{choose:[entry:PromptEntry]}>()
const menu=ref<HTMLElement>()
watch(()=>props.active,()=>nextTick(()=>menu.value?.querySelector<HTMLElement>('[aria-selected="true"]')?.scrollIntoView({block:'nearest'})))
</script>
<template><div ref="menu" id="prompt-command-menu" class="prompt-command-menu" role="listbox" :aria-label="kind==='slash'?'会话命令与科研 Skill':'可引用的材料与 Skill'"><span class="section-mini-label">{{kind==='slash'?'会话命令与科研 Skill':'引用论文、PDF 或笔记'}} · ↑ ↓ 选择 · Enter 确认</span><button v-for="(entry,index) in entries" :id="'prompt-option-'+index" :key="entry.kind+entry.id" type="button" role="option" :aria-selected="active===index" :class="{active:active===index}" @pointerdown.prevent @click="$emit('choose',entry)"><strong>{{entry.name}}</strong><small>{{entry.kind==='command'?entry.category:entry.kind==='skill'?'科研 Skill':entry.kind==='document'?'PDF':entry.kind==='note'?'笔记':'论文'}} · {{entry.description||entry.id}}</small></button><p v-if="!entries.length" class="settings-hint">没有匹配项，请先添加资料或创建 Skill。</p></div></template>
