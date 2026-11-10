<script setup lang="ts">
import {useRoute} from 'vue-router'
import {computed,nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {useResearchStore} from '../stores/research'
import {useBackendStore,errorText} from '../stores/backend'
import {useCopilotStore} from '../stores/copilot'
import {backendApi} from '../services/backendApi'
import {useComposerStore} from '../stores/composer'
import {readLocal} from '../lib/storage'
import type {ResearchNote} from '../types/copilot'
import Icon from './AppIcon.vue'
import ContextCard from './ResearchContextCard.vue'
import Composer from './ResearchComposer.vue'
import Messages from './BackendConversation.vue'
import Copilot from './ResearchCopilot.vue'
import History from './CopilotHistory.vue'
import Notes from './ResearchNotesPanel.vue'
const route=useRoute()
const emit=defineEmits<{close:[]}>(),research=useResearchStore(),backend=useBackendStore(),copilot=useCopilotStore()
const view=ref<'chat'|'analysis'|'notes'|'history'>('chat'),scroll=ref<HTMLElement>(),error=ref(''),notes=ref<ResearchNote[]>([]),historyMounted=ref(false),notesMounted=ref(false)
const showSuggestions=ref(true)
const spaceConversations=computed(()=>{const index=readLocal<Record<string,string>>(`orbit:conversation-index:${backend.user?.id}`,{});return [...new Set([backend.conversationId,...Object.keys(index).filter(id=>index[id]===backend.spaceId)])].filter(Boolean)})
let disposed=false
async function refreshNotes(){if(!backend.user){notes.value=[];return}const owner=backend.user.id,space=backend.spaceId;try{const data=await backendApi.notes();if(!disposed&&owner===backend.user?.id&&space===backend.spaceId)notes.value=data.items.filter(n=>spaceConversations.value.includes(n.conversationId))}catch(e){if(!disposed)error.value=errorText(e)}}
async function fresh(){error.value='';try{if(copilot.busy)throw new Error('请先停止当前科研任务。');await backend.newConversation();view.value='chat';showSuggestions.value=true;await refreshNotes()}catch(e){error.value=errorText(e)}}
function setView(value:typeof view.value){view.value=value;if(value==='history')historyMounted.value=true;if(value==='notes'){notesMounted.value=true;void refreshNotes()}}
async function formula(){const draft=useComposerStore().forSpace(backend.user?.id||'guest',research.activeId||'default');if(!draft.text.trim()){error.value='请先在下方输入需要解释的公式或章节原文。';return}copilot.focus=draft.text;await copilot.analyze('formula')}
function following(){const el=scroll.value;return !!el&&el.scrollHeight-el.scrollTop-el.clientHeight<100}
function bottom(force=false){const follow=force||following();if(follow)void nextTick(()=>{if(scroll.value)scroll.value.scrollTop=scroll.value.scrollHeight})}
watch(()=>backend.messages.at(-1)?.content,()=>{if(view.value==='chat')bottom()},{flush:'pre'})
watch(()=>[research.selectedPaperId,research.selectedIds.join(','),research.selectedRelation?.join(',')],()=>{showSuggestions.value=true})
watch(()=>[backend.conversationId,backend.user?.id,copilot.notes.map(n=>n.id+':'+n.revision).join(',')],()=>{void refreshNotes()})
watch(()=>copilot.state,value=>{if(value==='loading'||value==='error'||value==='success'||value==='insufficient_data'){view.value='analysis';if(value==='success'||value==='insufficient_data')showSuggestions.value=true}})
watch(()=>copilot.tab,value=>{if(value==='notes')setView('notes')})
watch(()=>copilot.taskBusy,value=>{if(value)view.value='analysis'})
watch(()=>[backend.spaceId,backend.user?.id],()=>{historyMounted.value=false;view.value='chat'})
onMounted(async()=>{await backend.init();await copilot.restore();await refreshNotes();if(route.query.history){backend.historyState=route.query.history==='deleted'?'deleted':'active';setView('history')}})
onBeforeUnmount(()=>disposed=true)
</script>
<template><aside id="orbit-research-assistant" class="assistant-panel research-copilot-sidebar" aria-label="AI 研究助手"><header class="assistant-header"><div><Icon name="Sparkles" :size="18"/><strong>Orbit AI</strong></div><div><button class="icon-button" aria-label="历史对话与分析记录" :aria-pressed="view==='history'" @click="setView(view==='history'?'chat':'history')"><Icon name="MessageSquare" :size="17"/></button><button class="icon-button" aria-label="新建对话" :disabled="backend.busy||backend.attaching||copilot.busy" @click="fresh"><Icon name="Plus" :size="18"/></button><button class="icon-button" aria-label="关闭研究助手" @click="emit('close')"><Icon name="PanelRightClose" :size="17"/></button></div></header>
<ContextCard @notes="setView('notes')"/><nav class="copilot-local-nav" aria-label="助手内容"><button :aria-pressed="view==='chat'" @click="setView('chat')">对话</button><button :aria-pressed="view==='analysis'" @click="setView('analysis')">科研分析</button><button :aria-pressed="view==='notes'" @click="setView('notes')">空间笔记</button></nav>
<div ref="scroll" class="assistant-content copilot-scroll"><p v-if="error" role="alert" class="form-error">{{error}}</p><div v-show="view==='chat'"><div v-if="!backend.messages.length" class="copilot-empty"><span class="eyebrow">YOUR RESEARCH COPILOT</span><h2>让问题，推动下一步。</h2><p>输入问题，或引用资料后一起思考。</p></div><div v-if="showSuggestions||!backend.messages.length" class="suggestion-area"><Copilot suggestions @analysis="view='analysis'"/><button v-if="backend.messages.length" class="text-button collapse-suggestions" @click="showSuggestions=false">收起建议</button></div><Messages v-if="backend.user"/><p v-else class="settings-hint">登录并配置模型后开始。<RouterLink to="/settings#account">连接个人空间 ↗</RouterLink></p></div>
<div v-show="view==='analysis'"><Copilot results/><details class="copilot-extra-actions"><summary>更多科研操作</summary><div class="copilot-actions"><button v-for="task in (['experiment','conclusions'] as const)" :key="task" :disabled="copilot.busy||backend.busy" @click="copilot.analyze(task)">{{task==='experiment'?'实验结果':'研究结论对比'}}</button><button :disabled="copilot.busy" @click="formula">解释公式或章节</button><button :disabled="copilot.saving" @click="copilot.saveSelection()">保存阅读清单</button></div><small>解释公式：先在下方输入原文片段，通过 / 论文解读提交；也可在阅读器选区问 AI。不编造公式或页码。</small></details></div>
<History :active="view==='history'" v-if="historyMounted" v-show="view==='history'" @restored="view=copilot.current?'analysis':'chat';bottom(true)"/><Notes v-if="notesMounted" v-show="view==='notes'" :conversation-ids="spaceConversations" :notes="notes" @changed="refreshNotes"/>
</div><Composer :notes="notes" @sent="view=$event?'analysis':'chat';showSuggestions=false;bottom(true)" @analysis="view='analysis'" @history="setView('history')" @fresh="view='chat'"/></aside></template>
