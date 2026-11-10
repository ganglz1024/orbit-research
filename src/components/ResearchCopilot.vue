<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import {useCopilotStore} from '../stores/copilot'
import {useBackendStore} from '../stores/backend'
import {useResearchStore} from '../stores/research'
import {useComposerStore} from '../stores/composer'
import {taskLabels,type AnalysisTask} from '../types/copilot'
import ResultView from './AnalysisResult.vue'
import Markdown from './ResearchMarkdown.vue'
defineProps<{suggestions?:boolean;results?:boolean}>()
const copilot=useCopilotStore(),backend=useBackendStore(),research=useResearchStore(),composer=useComposerStore(),more=ref(false)
const emit=defineEmits<{analysis:[]}>()
const primary=computed<AnalysisTask[]>(()=>copilot.context==='single'?['plain','method','contribution','limitations']:copilot.context==='relation'?['relation','methods']:['compare','methods','common'])
function prepare(id:string){const draft=composer.forSpace(backend.user?.id||'guest',research.activeId||'default');draft.skillId=id;document.querySelector<HTMLTextAreaElement>('#assistant-question')?.focus()}
async function analyze(task:AnalysisTask){await copilot.analyze(task);emit('analysis')}
watch(()=>copilot.context,()=>more.value=false)
</script>
<template><section class="copilot-inline" aria-label="科研任务助手"><details v-if="suggestions" class="contextual-suggestions" open><summary>下一步可以做什么</summary><div class="copilot-actions"><template v-if="copilot.context==='question'"><button @click="prepare('research_plan')">规划研究路线</button><button @click="prepare('keywords')">提取检索关键词</button><button @click="research.paperEditorOpen=true">添加研究论文</button></template><template v-else><button v-for="task in primary" :key="task" :disabled="copilot.busy||backend.busy" @click="analyze(task)">{{taskLabels[task]}}</button><button v-if="copilot.context==='multiple'" :disabled="copilot.busy||backend.busy" @click="analyze('compare')">生成对比表</button><button v-if="copilot.context==='relation'" @click="more=!more">查看关系证据</button></template></div><p v-if="more&&research.selectedRelationDetail" class="settings-hint">{{research.selectedRelationDetail.reason}} · AI 主题推断，不是真实引用。</p></details>
<template v-if="results"><div v-if="copilot.taskBusy||copilot.state==='loading'" class="analysis-loading" role="status">正在请求所选资料的科研分析…</div><p v-if="copilot.error" class="form-error" role="alert">{{copilot.error}}</p><article v-if="copilot.taskResult" class="copilot-task-result"><small>AI 辅助分析 · 请核验</small><h3>{{copilot.taskResult.title}}</h3><Markdown :text="copilot.taskResult.body"/><button class="text-button" :disabled="copilot.saving" @click="copilot.saveTask()">保存到空间笔记</button></article><p v-if="copilot.contextChanged" class="analysis-scope">当前选择已改变，结果保留生成时的来源快照。</p><ResultView v-if="copilot.current?.result" :analysis="copilot.current" @expand="copilot.tableOpen=true"/><button v-if="copilot.current?.result" class="text-button" :disabled="copilot.saving" @click="copilot.saveAnalysis()">保存分析到空间笔记</button></template></section></template>