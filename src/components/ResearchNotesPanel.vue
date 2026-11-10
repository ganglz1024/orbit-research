<script setup lang="ts">
import { computed,onMounted,ref,watch } from 'vue'
import { useCopilotStore } from '../stores/copilot'
import { useBackendStore,errorText } from '../stores/backend'
import { backendApi } from '../services/backendApi'
import { downloadBlob } from '../lib/storage'
import { researchNoteMarkdown } from '../lib/researchNote'
import type {ResearchNote,ResearchAnalysis} from '../types/copilot'
import ResultView from './AnalysisResult.vue'
const props=defineProps<{allSpaces?:boolean;conversationIds?:string[];notes?:ResearchNote[]}>(),copilot=useCopilotStore(),backend=useBackendStore(),all=ref<ResearchNote[]>([]),loading=ref(false),error=ref(''),editing=ref<string|null>(null),title=ref(''),body=ref(''),saving=ref(false)
const emit=defineEmits<{changed:[]}>()
const items=computed(()=>props.notes??(props.allSpaces||props.conversationIds?all.value:copilot.notes))
async function load(){if(!backend.user)return;if(props.notes){emit('changed');return}loading.value=true;error.value='';try{if(props.allSpaces||props.conversationIds){const ids=props.conversationIds;all.value=(await backendApi.notes()).items.filter(n=>!ids||ids.includes(n.conversationId))}else await copilot.refreshNotes()}catch(e){error.value=errorText(e)}finally{loading.value=false}}
onMounted(async()=>{await backend.init();await load()});watch(()=>[backend.conversationId,props.conversationIds?.join(',')],()=>void load())
function edit(n:ResearchNote){editing.value=n.id;title.value=n.title;body.value=n.body}
async function save(n:ResearchNote){saving.value=true;try{await backendApi.editNote(n.id,{title:title.value,body:body.value,revision:n.revision});editing.value=null;await load();emit('changed')}catch(e){error.value=errorText(e)}finally{saving.value=false}}
</script>
<template><section class="research-notes-panel"><div class="model-conversation-heading"><h3>{{allSpaces?'研究笔记与分析':'当前空间笔记'}}</h3><button class="text-button" :disabled="loading" @click="load">刷新</button></div><p v-if="!backend.user" class="settings-hint">登录后可保存和读取研究笔记。<RouterLink to="/settings">连接个人空间</RouterLink></p><p v-else-if="loading" role="status">正在读取笔记…</p><p v-else-if="!items.length" class="settings-hint">还没有保存的笔记。可从精读、比较结果或阅读清单中保存。</p><p v-if="error" class="form-error" role="alert">{{error}}</p><article v-for="note in items" :key="note.id" class="research-note-card"><span class="section-mini-label">{{note.analysisId?'AI 分析快照 + 用户笔记':'研究笔记'}} · v{{note.revision}}</span><template v-if="editing===note.id"><label>笔记标题<input v-model="title" maxlength="180"/></label><label>我的笔记<textarea v-model="body" rows="5" maxlength="12000"/></label><div class="button-row"><button class="secondary-button" :disabled="saving||!title.trim()" @click="save(note)">保存笔记修改</button><button class="text-button" :disabled="saving" @click="editing=null">取消</button></div></template><template v-else><h4>{{note.title}}</h4><p class="analysis-detail-text">{{note.body}}</p><div class="button-row"><button class="text-button" @click="edit(note)">编辑笔记</button><button class="text-button" @click="downloadBlob(researchNoteMarkdown(note),'Orbit-研究笔记-'+note.id+'.md','text/markdown;charset=utf-8')">导出 Markdown</button></div><details v-if="'result' in note.snapshot&&note.snapshot.result"><summary>查看保存时的分析与来源</summary><ResultView :analysis="note.snapshot as ResearchAnalysis" expanded/></details><p v-else class="settings-hint">{{note.snapshot.sources.map(s=>s.title).join(' / ')}}</p></template></article></section></template>
