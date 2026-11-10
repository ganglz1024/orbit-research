import { computed,ref,watch } from 'vue'
import { defineStore,acceptHMRUpdate } from 'pinia'
import { useResearchStore } from './research'
import { useBackendStore,errorText } from './backend'
import { backendApi,BackendApiError } from '../services/backendApi'
import {paperEvidence} from '../lib/paperSources'
import {runResearchTask} from '../services/researchTasks'
import {nextTick} from 'vue'
import { taskLabels,type AnalysisTask,type AnalysisPaper,type ResearchAnalysis,type AnalysisState,type ResearchNote } from '../types/copilot'

export const useCopilotStore=defineStore('copilot',()=>{
  const research=useResearchStore(),backend=useBackendStore(),questionMode=ref(false),sourceMode=ref<'papers'|'materials'>('papers'),bindings=ref<Record<string,string>>({})
  const open=ref(true),tableOpen=ref(false),tab=ref<'analysis'|'notes'>('analysis'),state=ref<AnalysisState>('idle'),error=ref(''),focus=ref(''),current=ref<ResearchAnalysis|null>(null),history=ref<ResearchAnalysis[]>([]),notes=ref<ResearchNote[]>([]),saving=ref(false)
  let taskController:AbortController|undefined
  let timer:ReturnType<typeof setTimeout>|undefined,generation=0,retry:{signature:string;key:string}|undefined
  const paperIds=computed(()=>questionMode.value?[]:research.selectedRelation??(research.selectedPaperId?[research.selectedPaperId]:research.selectedIds))
  const sources=computed<AnalysisPaper[]>(()=>sourceMode.value==='materials'?backend.attached.map(d=>({id:'doc_'+d.id,title:d.name,documentId:d.id})):paperIds.value.map(research.paperById).filter(p=>!!p).map(p=>({...paperEvidence(p),documentId:bindings.value[p.id]||p.backendDocumentId})))
  const admitting=ref(false),taskBusy=ref(false),taskResult=ref<{title:string;body:string;workspaceId:string;papers:AnalysisPaper[];runId:string}|null>(null)
  const busy=computed(()=>taskBusy.value||admitting.value||state.value==='loading'||research.organizingPapers||research.connectingPapers)
  const context=computed(()=>sourceMode.value==='papers'&&research.selectedRelation?'relation':sources.value.length>1?'multiple':sources.value.length===1?'single':'question')
  const contextChanged=computed(()=>!!current.value&&JSON.stringify(current.value.sources.map(s=>[s.id,s.documentId||'']))!==JSON.stringify(sources.value.map(s=>[s.id,s.documentId||''])))
  watch(()=>[research.selectedPaperId,research.selectedRelation,research.selectedIds.join(',')],()=>{sourceMode.value='papers';questionMode.value=false;tab.value='analysis'})
  watch(()=>backend.conversationId,()=>{generation++;if(timer)clearTimeout(timer);current.value=null;history.value=[];notes.value=[];state.value='idle';error.value='';bindings.value={};void restore()})
  watch(()=>research.activeId,()=>{taskController?.abort();taskResult.value=null})
  watch(()=>backend.user,value=>{if(!value){generation++;if(timer)clearTimeout(timer);current.value=null;notes.value=[];history.value=[];state.value='idle'}})
  async function restore(){if(!backend.user||!backend.conversationId)return;const id=backend.conversationId;try{const data=await backendApi.analyses(id);if(id!==backend.conversationId)return;history.value=data.items;await refreshNotes()}catch(e){error.value=errorText(e)}}
  async function poll(id:string,epoch:number){try{const value=await backendApi.analysis(id);if(epoch!==generation)return;current.value=value;state.value=value.state;error.value=value.error;if(value.state==='loading')timer=setTimeout(()=>void poll(id,epoch),900);else history.value=[value,...history.value.filter(a=>a.id!==id)]}catch(e){if(epoch===generation){state.value='error';error.value=errorText(e)}}}
  async function analyze(task:AnalysisTask,explicitSources?:AnalysisPaper[]){
    if(busy.value)return;tab.value='analysis';open.value=true;error.value=''
    if(!backend.user||!backend.model?.configured){state.value='error';error.value='请先在空间设置中登录并配置模型。分析不会使用预置回答替代。';return}
    if(backend.busy){error.value='请先完成或停止当前模型对话。';return}
    const selectedSources=explicitSources||sources.value
    if(!selectedSources.length||selectedSources.length>5){state.value='insufficient_data';error.value='精读请选择一篇，比较请选择 2–5 篇论文或 PDF。';return}
    const capturedPaperIds=explicitSources?explicitSources.map(s=>s.id).filter(id=>!!research.paperById(id)):[...paperIds.value],capturedFocus=focus.value,snapshot=JSON.parse(JSON.stringify(selectedSources)) as AnalysisPaper[]
    const space=research.activeId,owner=backend.user.id
    const valid=()=>space===research.activeId&&owner===backend.user?.id
    admitting.value=true;state.value='loading';current.value=null;tableOpen.value=['compare','common','methods','conclusions'].includes(task)
    try{const id=await backend.ensureConversation();for(const pid of capturedPaperIds){const p=research.paperById(pid);if(p?.localDocumentId){const doc=await backend.attachLocal(p.localDocumentId);if(doc){research.bindDocument(p.localDocumentId,doc.id);const source=snapshot.find(s=>s.id===pid);if(source)source.documentId=doc.id}}}for(const s of snapshot)if(s.documentId)await backendApi.associateDocument(id,s.documentId);state.value='loading';const signature=JSON.stringify([id,task,snapshot,capturedFocus]);if(retry?.signature!==signature)retry={signature,key:crypto.randomUUID()};const epoch=++generation
      if(!valid())throw new Error('研究空间已改变，本次分析未应用到新空间。')
      const value=await backendApi.createAnalysis(id,{task,papers:snapshot,focus:capturedFocus},retry.key);retry=undefined;if(epoch!==generation||!valid())return;current.value=value;state.value=value.state;if(value.state==='loading')void poll(value.id,epoch);else{error.value=value.error;history.value=[value,...history.value.filter(a=>a.id!==value.id)]}
    }catch(e){if(!valid())return;state.value=e instanceof BackendApiError&&['DOCUMENT_NOT_READABLE_BY_AI','PAPER_COUNT','FORMULA_REQUIRED','ANALYSIS_CONTEXT_LIMIT'].includes(e.code)?'insufficient_data':'error';error.value=errorText(e)}finally{admitting.value=false}
  }
  async function assist(task:'plan'|'gaps',explicitSources?:AnalysisPaper[],request=''){
    if(busy.value)return
    let expectedSpace=research.activeId;const expectedOwner=backend.user?.id
    taskBusy.value=true;error.value='';tab.value='analysis';taskResult.value=null;taskController=new AbortController()
    try{if(!backend.user||!backend.model?.configured)throw new Error('请先登录并配置模型 API。')
      const topic=research.question.trim();if(!topic)throw new Error('请先填写研究问题。')
      const snapshot=JSON.parse(JSON.stringify(explicitSources||sources.value)) as AnalysisPaper[]
      const space=research.activeId,owner=backend.user.id
      if(task==='gaps'&&snapshot.length<2)throw new Error('研究空白分析至少需要两篇明确选择的资料。')
      research.startQuestion(topic);expectedSpace=research.activeId;await nextTick();await backend.switchWorkspace(research.activeId);const workspace=await backend.ensureConversation()
      const taskTitle=task==='plan'?'研究路线规划':'研究空白辅助分析'
      const instruction=task==='plan'?'按研究问题、关键概念、中英文检索关键词、建议阅读顺序、基础知识与后续问题组织研究路线。不编造已检索到的论文，阅读路线是建议而非唯一正确路径。':'按研究问题、现有证据、方法局限、候选研究方向分析。明确标注AI推测和需要验证的条件，不得声称尚无人研究或必然具有创新性。'
      const localIds=explicitSources?[]:snapshot.flatMap(s=>{const p=research.paperById(s.id);return p?.localDocumentId?[p.localDocumentId]:[]})
      const result=await runResearchTask(instruction+'\n研究主题：'+topic+'\n用户补充要求：'+request.slice(0,1500)+'\n明确提供的元数据：'+JSON.stringify(snapshot.map(s=>({...s,metadata:s.metadata?.slice(0,700)}))),{title:taskTitle,signal:taskController.signal,localDocumentIds:localIds,documentIds:snapshot.flatMap(s=>s.documentId?[s.documentId]:[])})
      if(taskController.signal.aborted||space!==research.activeId||owner!==backend.user?.id)throw new Error('研究空间或账户已改变，结果未应用到新空间。')
      taskResult.value={title:taskTitle,body:result.answer,workspaceId:workspace,papers:snapshot,runId:result.runId}
    }catch(e){if(expectedSpace===research.activeId&&expectedOwner===backend.user?.id)error.value=errorText(e)}finally{taskBusy.value=false}
  }
  function stopTask(){taskController?.abort()}
  async function saveTask(){if(!taskResult.value||saving.value)return;saving.value=true;try{const result=taskResult.value;await backendApi.saveNote(result.workspaceId,{title:result.title,body:result.body+'\n\n模型任务：'+result.runId+'\n来源范围：'+result.papers.map(p=>p.title).join('；')},'task-note-'+result.runId);await refreshNotes();research.notify('AI 分析已保存到空间笔记。')}catch(e){error.value=errorText(e)}finally{saving.value=false}}
  async function cancel(){if(!current.value)return;try{generation++;if(timer)clearTimeout(timer);current.value=await backendApi.cancelAnalysis(current.value.id);state.value=current.value.state;error.value=current.value.error}catch(e){error.value=errorText(e)}}
  async function refreshNotes(all=false){if(!backend.user)return;if(!all&&!backend.conversationId){notes.value=[];return}const id=backend.conversationId,owner=backend.user.id;const data=await backendApi.notes(all?undefined:id);if(id===backend.conversationId&&owner===backend.user?.id)notes.value=data.items}
  async function saveAnalysis(){if(!current.value?.result||saving.value)return;saving.value=true;error.value='';try{const value=current.value;const body=[value.result!.summary,value.result!.details].filter(Boolean).join('\n\n');await backendApi.saveNote(value.conversationId,{analysisId:value.id,title:value.result!.title||taskLabels[value.task],body},'analysis-note-'+value.id);await refreshNotes();research.notify('分析结果与来源快照已保存到当前空间笔记。')}catch(e){error.value=errorText(e)}finally{saving.value=false}}
  async function saveSelection(){if(!backend.user){error.value='请先登录后保存研究笔记。';open.value=true;return}if(!sources.value.length)return;const papers=JSON.parse(JSON.stringify(sources.value)) as AnalysisPaper[];saving.value=true;try{const id=await backend.ensureConversation();await backendApi.saveNote(id,{title:'阅读清单 · '+research.question.slice(0,80),body:'待阅读与核验的论文清单。',papers},crypto.randomUUID());await refreshNotes();tab.value='notes';open.value=true;research.notify('阅读清单已保存。')}catch(e){error.value=errorText(e)}finally{saving.value=false}}
  function show(value:ResearchAnalysis){if(busy.value)return;current.value=value;state.value=value.state;error.value=value.error;tab.value='analysis';if(value.state==='loading')void poll(value.id,++generation)}
  return{sourceMode,questionMode,bindings,open,tableOpen,tab,state,error,focus,current,history,notes,saving,sources,busy,context,contextChanged,taskBusy,taskResult,assist,stopTask,saveTask,analyze,cancel,restore,refreshNotes,saveAnalysis,saveSelection,show}
})

if(import.meta.hot)import.meta.hot.accept(acceptHMRUpdate(useCopilotStore,import.meta.hot))
