import { computed,ref } from 'vue'
import { defineStore,acceptHMRUpdate } from 'pinia'
import { readLocal,writeLocal,listDocuments,deleteDocument } from '../lib/storage'
import { refreshPresetDeckCopy,refreshPresetSessionCopy } from '../lib/legacyPresetCopy'
import {requestedSlides} from '../lib/taskIntent'
import { runResearchTask } from '../services/researchTasks'
import { paperEvidence } from '../lib/paperSources'
import { organizePaper,analyzePaperRelations } from '../services/paperOrganization'
import {reorderWorkspace} from '../lib/workspaceLayout'
import { pruneSessionPapers } from '../lib/paperRemoval'
import { useBackendStore } from './backend'
import type { Paper,PaperRelation,Deck,DocumentMeta,Message,Session,Slide } from '../types'
export const useResearchStore=defineStore('research',()=>{
  const papers=ref<Paper[]>(readLocal<Paper[]>('orbit:user-papers',[]))
  const sessions=ref<Session[]>(readLocal<Session[]>('orbit:sessions',[]).map(refreshPresetSessionCopy))
  const deletedSessions=ref<Array<{session:Session;index:number;deletedAt:string}>>(readLocal('orbit:deleted-sessions',[]))
  const activeId=ref(''),question=ref(''),visibleIds=ref<string[]>([]),selectedIds=ref<string[]>([]),messages=ref<Message[]>([]),isSearching=ref(false)
  const selectedPaperId=ref<string|null>(null),selectedRelation=ref<[string,string]|null>(null),uploadOpen=ref(false),paperEditorOpen=ref(false),mobileMenu=ref(false),documents=ref<DocumentMeta[]>([])
  const decks=ref<Deck[]>(readLocal<Deck[]>('orbit:decks',[]).map(refreshPresetDeckCopy)),generatingDeck=ref(false),connectingPapers=ref(false)
  const organizingPapers=ref(false),processingPaperId=ref(''),processingLabel=ref(''),processingError=ref(''),paperEditorId=ref('')
  const removalTarget=ref<{id:string;kind:'paper'|'document';name:string}|null>(null),removing=ref(false),removalError=ref('')
  const incompletePapers=computed(()=>activePapers.value.filter(p=>p.localDocumentId&&(p.metadataState==='error'||p.metadataState==='insufficient_data'||!p.metadataRunId)))
  const completedUpload=ref<{ids:string[];stamp:number}|null>(null)
  const autoOrganizePapers=ref(readLocal('orbit:auto-organize-papers',true))
  let processingController:AbortController|undefined
  const reducedMotion=ref(readLocal('orbit:reduced-motion',false)),disabledSkills=ref<string[]>(readLocal('orbit:disabled-skills',[])),toast=ref('')
  const paperById=(id:string)=>papers.value.find(p=>p.id===id)
  const activePapers=computed(()=>visibleIds.value.map(paperById).filter((p):p is Paper=>!!p))
  const selectedPaper=computed(()=>paperById(selectedPaperId.value||''))
  const edges=ref<[string,string][]>([])
  const relationDetails=ref<PaperRelation[]>([])
  const selectedRelationDetail=computed(()=>relationDetails.value.find(e=>selectedRelation.value?.includes(e.from)&&selectedRelation.value?.includes(e.to)))
  let toastTimer:ReturnType<typeof setTimeout>
  function notify(text:string){toast.value=text;clearTimeout(toastTimer);toastTimer=setTimeout(()=>toast.value='',4200)}
  function persist(){
    if(!activeId.value)return
    const existing=sessions.value.find(s=>s.id===activeId.value)
    const snapshot:Session={id:activeId.value,title:existing?.customTitle||question.value.slice(0,24)||'新的研究空间',customTitle:existing?.customTitle,query:question.value,paperIds:[...visibleIds.value],selectedIds:[...selectedIds.value],messages:[...messages.value],edges:[...edges.value],relationDetails:[...relationDetails.value],createdAt:existing?.createdAt||new Date().toISOString()}
    const index=sessions.value.findIndex(s=>s.id===activeId.value)
    if(index<0)sessions.value=[snapshot,...sessions.value];else sessions.value=sessions.value.map(s=>s.id===snapshot.id?snapshot:s)
    try{writeLocal('orbit:sessions',sessions.value);writeLocal('orbit:user-papers',papers.value)}catch{notify('本地存储已满，本次修改尚未保存。')}
  }
  function saveSessionList(next:Session[],trash=deletedSessions.value){
    try{writeLocal('orbit:sessions',next);writeLocal('orbit:deleted-sessions',trash)}catch{try{writeLocal('orbit:sessions',sessions.value);writeLocal('orbit:deleted-sessions',deletedSessions.value)}catch{};notify('无法保存研究空间的变更，请检查浏览器存储。');return false}
    sessions.value=next;deletedSessions.value=trash;return true
  }
  function renameSession(id:string,name:string){const title=name.trim();if(!title||title.length>80){notify('空间名称需要 1–80 个字符。');return false}if(!sessions.value.some(s=>s.id===id))return false;const saved=saveSessionList(sessions.value.map(s=>s.id===id?{...s,title,customTitle:title}:s));if(saved)notify('研究空间已重命名。');return saved}
  function reorderSession(id:string,target:string,after=false){const next=reorderWorkspace(sessions.value,id,target,after);if(next===sessions.value)return true;return saveSessionList(next)}
  function moveSession(id:string,delta:number){const index=sessions.value.findIndex(s=>s.id===id),target=sessions.value[index+delta];if(!target)return false;return reorderSession(id,target.id,delta>0)}
  function deleteSession(id:string){
    if(useBackendStore().busy||useBackendStore().attaching||organizingPapers.value||connectingPapers.value){notify('请先完成或停止当前任务，再删除研究空间。');return false}
    const index=sessions.value.findIndex(s=>s.id===id),session=sessions.value[index];if(!session)return false
    const next=sessions.value.filter(s=>s.id!==id),trash=[{session:JSON.parse(JSON.stringify(session)) as Session,index,deletedAt:new Date().toISOString()},...deletedSessions.value.filter(s=>s.session.id!==id)]
    if(!saveSessionList(next,trash))return false
    if(activeId.value===id){if(next.length)loadSession(next[Math.min(index,next.length-1)]!.id);else newSpace()}
    notify('研究空间已移到「已删除研究」，可以恢复。');return true
  }
  function restoreSession(id:string){const record=deletedSessions.value.find(s=>s.session.id===id);if(!record||sessions.value.some(s=>s.id===id))return false;const next=[...sessions.value];next.splice(Math.min(record.index,next.length),0,record.session);const saved=saveSessionList(next,deletedSessions.value.filter(s=>s.session.id!==id));if(saved)notify('研究空间已恢复。');return saved}

  function stopPaperProcessing(){processingController?.abort()}
  function newSpace(){stopPaperProcessing();activeId.value=crypto.randomUUID();question.value='';visibleIds.value=[];selectedIds.value=[];selectedPaperId.value=null;selectedRelation.value=null;messages.value=[];edges.value=[];relationDetails.value=[];persist();return activeId.value}
  function startQuestion(text:string){if(!activeId.value)newSpace();if(!question.value)question.value=text.trim().slice(0,180);persist()}
  function addPaper(input:Pick<Paper,'title'|'authors'|'year'|'summary'> & Partial<Paper>){
    if(!input.title.trim())throw new Error('请输入论文标题。')
    if(input.url&&!/^https?:\/\//i.test(input.url))throw new Error('论文链接需要使用 http 或 https。')
    if(!activeId.value)newSpace()
    const existing=input.localDocumentId?papers.value.find(p=>p.localDocumentId===input.localDocumentId):undefined
    if(existing){if(!visibleIds.value.includes(existing.id))visibleIds.value.push(existing.id);persist();return existing}
    const index=visibleIds.value.length,p:Paper={...input,id:'paper_'+crypto.randomUUID(),title:input.title.trim(),shortTitle:input.title.trim(),authors:input.authors||'作者待补充',year:input.year||0,arxiv:'',category:'foundation',tag:input.localDocumentId?'上传 PDF':'用户添加',summary:input.summary||'',insight:'',questions:[],x:50+(index%3)*330,y:40+Math.floor(index/3)*270}
    papers.value.push(p);visibleIds.value.push(p.id);selectedPaperId.value=p.id;persist();return p
  }
  function addPdfPaper(doc:DocumentMeta){return addPaper({title:doc.name.replace(/\.pdf$/i,''),authors:'',year:0,summary:'',localDocumentId:doc.id,source:'pdf'})}
  function bindDocument(localId:string,backendId:string){for(const p of papers.value)if(p.localDocumentId===localId)p.backendDocumentId=backendId;persist()}
  function loadSession(id:string){const s=sessions.value.find(s=>s.id===id);if(!s)return;if(activeId.value!==id)stopPaperProcessing();activeId.value=id;question.value=s.query;visibleIds.value=s.paperIds.filter(id=>!!paperById(id));selectedIds.value=s.selectedIds.filter(id=>!!paperById(id));messages.value=[...s.messages];selectedPaperId.value=null;selectedRelation.value=null;edges.value=(s.edges||[]).filter(([a,b])=>!!paperById(a)&&!!paperById(b));relationDetails.value=(s.relationDetails||[]).filter(e=>!!paperById(e.from)&&!!paperById(e.to))}
  function togglePaper(id:string){selectedPaperId.value=null;selectedRelation.value=null;selectedIds.value=selectedIds.value.includes(id)?selectedIds.value.filter(x=>x!==id):[...selectedIds.value,id];persist()}
  async function search(input:string){startQuestion(input);paperEditorOpen.value=true}
  function cancel(){isSearching.value=false}
  function expand(){paperEditorOpen.value=true}
  function ask(_input:string){notify('请先连接模型 API，再向研究助手提问。');return false}
  async function refreshDocuments(){try{documents.value=(await listDocuments()).filter(d=>!d.demo)}catch{notify('无法访问本地文件存储，请检查浏览器隐私设置。')}}
  function processingTargets(ids:string[]){return [...new Set(ids)].map(paperById).filter((p):p is Paper=>!!p)}
  async function requirePaperModel(){const backend=useBackendStore();await backend.init();if(!backend.user||!backend.model?.configured)throw new Error('请先登录并配置模型 API，再整理论文。');if(backend.busy)throw new Error('请先完成或停止当前模型对话。');return backend.user.id}
  function verifyProcessingScope(workspace:string,owner:string,signal:AbortSignal){if(signal.aborted||activeId.value!==workspace||useBackendStore().user?.id!==owner)throw new Error('整理已停止，已完成的资料会保留。')}
  function applyRelations(chosen:Paper[],details:PaperRelation[]){const ids=new Set(chosen.map(p=>p.id));edges.value=[...edges.value.filter(([a,b])=>!ids.has(a)||!ids.has(b)),...details.map(e=>[e.from,e.to] as [string,string])];relationDetails.value=[...relationDetails.value.filter(e=>!ids.has(e.from)||!ids.has(e.to)),...details];persist()}
  async function organizePapers(ids:string[]=visibleIds.value,withRelations=true){
    if(organizingPapers.value||connectingPapers.value)return false
    const chosen=processingTargets(ids),workspace=activeId.value
    processingError.value=''
    if(!chosen.length||chosen.length>12){processingError.value='一次可整理 1–12 篇当前空间论文。';notify(processingError.value);return false}
    organizingPapers.value=true
    processingController=new AbortController();const signal=processingController.signal
    try{
      const owner=await requirePaperModel();verifyProcessingScope(workspace,owner,signal)
      let completed=0;const failures:string[]=[]
      for(const [index,paper] of chosen.entries()){
        verifyProcessingScope(workspace,owner,signal);processingPaperId.value=paper.id;processingLabel.value=`正在整理 ${index+1}/${chosen.length}：${paper.title}`
        if(!paper.localDocumentId){failures.push(paper.title+'：未绑定 PDF，保留手填资料。');continue}
        try{
          const result=await organizePaper({...paper},signal);verifyProcessingScope(workspace,owner,signal)
          if(result.title){paper.title=result.title;paper.shortTitle=result.title}if(result.authors)paper.authors=result.authors;if(result.year){paper.year=result.year;paper.yearBasis=result.yearBasis}
          if(result.summary)paper.summary=result.summary
          paper.tag='上传 PDF'
          paper.metadataEvidence=result.evidence;paper.metadataRunId=result.runId;paper.metadataState=result.missing.length?'insufficient_data':'ready';paper.metadataError=result.missing.length?'待核验：'+result.missing.join('、'):''
          if(result.missing.length)failures.push(paper.title+'：'+paper.metadataError)
          completed++;persist()
        }catch(e){if(signal.aborted)throw e;paper.metadataState='error';paper.metadataError=e instanceof Error?e.message:'题录整理失败。';failures.push(paper.title+'：'+paper.metadataError);persist()}
      }
      verifyProcessingScope(workspace,owner,signal)
      const eligible=chosen.filter(p=>p.summary.trim().length>0)
      if(withRelations&&eligible.length>=2){processingPaperId.value='';processingLabel.value=`正在分析 ${eligible.length} 篇论文的主题联系…`;try{const details=await analyzePaperRelations(eligible,signal);verifyProcessingScope(workspace,owner,signal);applyRelations(eligible,details)}catch(e){if(signal.aborted)throw e;failures.push(e instanceof Error?e.message:'联系分析失败。')}}
      processingError.value=failures.join('\n');processingLabel.value=`已整理 ${completed}/${chosen.length} 篇 · ${relationDetails.value.length} 条 AI 主题联系`
      notify(processingLabel.value+(failures.length?'，部分资料需要核验。':'。'));return completed>0
    }catch(e){processingError.value=e instanceof Error?e.message:'论文整理失败。';processingLabel.value=processingController?.signal.aborted?'整理已停止，已完成的资料会保留。':'整理未完成，已有资料已保留。';notify(processingError.value);return false}finally{organizingPapers.value=false;processingPaperId.value=''}
  }
  async function connectSelected(ids?:string[]){
    if(connectingPapers.value||organizingPapers.value)return false
    const chosen=processingTargets(ids||(selectedIds.value.length>=2?selectedIds.value:visibleIds.value)),workspace=activeId.value
    if(chosen.length<2||chosen.length>12){processingError.value='主题联系分析支持 2–12 篇论文。';notify(processingError.value);return false}
    if(chosen.some(p=>p.localDocumentId&&!p.metadataRunId&&!p.summary.trim()))return organizePapers(chosen.map(p=>p.id))
    processingError.value=''
    connectingPapers.value=true
    processingController=new AbortController();const signal=processingController.signal
    try{const owner=await requirePaperModel();verifyProcessingScope(workspace,owner,signal);processingLabel.value=`正在分析 ${chosen.length} 篇论文的主题联系…`;const details=await analyzePaperRelations(chosen,signal);verifyProcessingScope(workspace,owner,signal);applyRelations(chosen,details);processingLabel.value=`生成 ${details.length} 条 AI 主题联系 · 请逐条核验`;notify(processingLabel.value);return true}catch(e){processingError.value=e instanceof Error?e.message:'关联分析失败。';processingLabel.value='主题联系分析未完成，已有连线已保留。';notify(processingError.value);return false}finally{connectingPapers.value=false}
  }
  function requestRemoval(id:string,kind:'paper'|'document'){
    if(organizingPapers.value||connectingPapers.value){notify('请先停止论文整理，再删除资料。');return}
    const name=kind==='paper'?paperById(id)?.title:documents.value.find(d=>d.id===id)?.name
    if(name){removalError.value='';removalTarget.value={id,kind,name}}
  }
  async function confirmRemoval(){
    const target=removalTarget.value;if(!target||removing.value)return
    if(organizingPapers.value||connectingPapers.value){removalError.value='请先停止整理任务。';return}
    removing.value=true;removalError.value=''
    try{
      const ids=new Set(target.kind==='paper'?[target.id]:papers.value.filter(p=>p.localDocumentId===target.id).map(p=>p.id))
      const nextSessions=sessions.value.map(s=>s.id===activeId.value||target.kind==='document'?pruneSessionPapers(s,ids):s)
      const nextPapers=target.kind==='document'?papers.value.filter(p=>!ids.has(p.id)):papers.value
      // Prepare localStorage before deleting the file; abort on quota/privacy failures.
      try{writeLocal('orbit:sessions',nextSessions);writeLocal('orbit:user-papers',nextPapers)}catch(error){try{writeLocal('orbit:sessions',sessions.value);writeLocal('orbit:user-papers',papers.value)}catch{/* Report the storage failure without deleting the PDF. */}throw error}
      try{if(target.kind==='document')await deleteDocument(target.id)}catch(error){writeLocal('orbit:sessions',sessions.value);writeLocal('orbit:user-papers',papers.value);throw error}
      sessions.value=nextSessions;papers.value=nextPapers
      visibleIds.value=visibleIds.value.filter(id=>!ids.has(id));selectedIds.value=selectedIds.value.filter(id=>!ids.has(id))
      edges.value=edges.value.filter(([a,b])=>!ids.has(a)&&!ids.has(b));relationDetails.value=relationDetails.value.filter(e=>!ids.has(e.from)&&!ids.has(e.to))
      if(selectedPaperId.value&&ids.has(selectedPaperId.value))selectedPaperId.value=null
      if(selectedRelation.value?.some(id=>ids.has(id)))selectedRelation.value=null
      if(target.kind==='document')documents.value=documents.value.filter(d=>d.id!==target.id)
      removalTarget.value=null;persist();notify(target.kind==='document'?'已删除此浏览器的 PDF、批注及相关卡片。':'已从当前研究空间移除，PDF 文件仍保留。')
    }catch(e){removalError.value=e instanceof Error?e.message:'删除失败，资料仍保留，请重试。'}finally{removing.value=false}
  }
  function setAutoOrganize(value:boolean){autoOrganizePapers.value=value;writeLocal('orbit:auto-organize-papers',value)}
  function editPaper(id:string){paperEditorId.value=id;paperEditorOpen.value=true}
  function updatePaper(id:string,input:Pick<Paper,'title'|'authors'|'year'|'summary'|'url'>){const paper=paperById(id);if(!paper)throw new Error('论文不存在。');if(!input.title.trim())throw new Error('请输入论文标题。');Object.assign(paper,input,{title:input.title.trim(),shortTitle:input.title.trim(),metadataState:undefined,metadataError:'',metadataRunId:undefined,metadataEvidence:[],tag:'用户校订'});persist()}
  async function createDeck(instructions='',authorized:{documentIds?:string[];localDocumentIds?:string[]}={}):Promise<Deck|undefined>{
    if(generatingDeck.value)return
    const taskTitle=question.value||'科研阅读汇报'
    const selected=(selectedIds.value.length?selectedIds.value:visibleIds.value).map(paperById).filter((p):p is Paper=>!!p).slice(0,5)
    if(!selected.length){notify('请先添加论文并选择汇报材料。');return}
    generatingDeck.value=true
    try{
      const count=requestedSlides(instructions)
      const result=await runResearchTask('请根据明确提供的论文资料生成科研阅读汇报。只返回 JSON：{"slides":[{"title":"标题","body":"内容","type":"cover|content|references","sources":["提供的论文ID"]}]}。'+(count?'严格输出'+count+'页（含封面与来源页）':'输出6到10页')+'，结论必须对应提供的资料，不编造引用、实验数字。仅有元数据时明确资料不足。最后一页列出提供的来源。每页只写科研汇报内容，不写仅返回JSON、尚未生成文件等模型执行状态；PPTX由应用生成和导出，文件状态由界面反馈，不属于幻灯片内容。资料范围集中在来源页说明，不要每页重复同一免责声明。额外要求：'+instructions.slice(0,600)+'。研究问题：'+taskTitle+'\n论文资料：'+JSON.stringify(selected.map(p=>({...paperEvidence(p),metadata:p.summary.slice(0,700)}))),{title:'生成科研汇报',localDocumentIds:authorized.localDocumentIds||[],documentIds:authorized.documentIds||[]})
      const raw=JSON.parse(result.answer.replace(/^```(?:json)?\s*|\s*```$/g,''));if(!Array.isArray(raw.slides)||raw.slides.length<2||raw.slides.length>12)throw new Error('模型未返回有效的汇报结构，请重试。')
      if(count&&raw.slides.length!==count)throw new Error('模型返回页数与要求不一致，未保存错误页数的汇报。')
      const allowed=new Set(selected.map(p=>p.id));const slides:Slide[]=raw.slides.map((s:any)=>{if(typeof s.title!=='string'||typeof s.body!=='string'||s.title.length>180||s.body.length>1800)throw new Error('模型返回的页面格式或长度不符合要求。');return {id:crypto.randomUUID(),title:s.title,body:s.body,type:['cover','content','references'].includes(s.type)?s.type:'content',sources:Array.isArray(s.sources)?s.sources.filter((id:string)=>allowed.has(id)):[]}})
      const deck:Deck={id:crypto.randomUUID(),title:taskTitle,slides,createdAt:new Date().toISOString(),version:1,runId:result.runId}
      decks.value.unshift(deck);saveDecks();notify('模型汇报已生成，请逐页审阅并核对来源。');return deck
    }catch(e){notify(e instanceof Error?e.message:'生成汇报失败，请重试。')}finally{generatingDeck.value=false}
  }
  function deleteDeck(id:string){
    if(!decks.value.some(d=>d.id===id))return false
    const next=decks.value.filter(d=>d.id!==id)
    try{writeLocal('orbit:decks',next)}catch{notify('删除未保存，请检查浏览器存储后重试。');return false}
    decks.value=next;notify('汇报草稿已删除。');return true
  }
  function saveDecks(){try{writeLocal('orbit:decks',decks.value)}catch{notify('成果尚未保存：浏览器存储空间不足。')}}
  function setReducedMotion(v:boolean){reducedMotion.value=v;writeLocal('orbit:reduced-motion',v);document.documentElement.dataset.reducedMotion=String(v)}
  function toggleSkill(id:string){disabledSkills.value=disabledSkills.value.includes(id)?disabledSkills.value.filter(x=>x!==id):[...disabledSkills.value,id];writeLocal('orbit:disabled-skills',disabledSkills.value)}
  return {deletedSessions,renameSession,reorderSession,moveSession,deleteSession,restoreSession,completedUpload,removalTarget,removing,removalError,incompletePapers,requestRemoval,confirmRemoval,papers,paperById,edges,relationDetails,selectedRelationDetail,sessions,activeId,question,visibleIds,selectedIds,messages,isSearching,selectedPaperId,selectedRelation,selectedPaper,uploadOpen,paperEditorOpen,paperEditorId,mobileMenu,documents,decks,generatingDeck,connectingPapers,organizingPapers,processingPaperId,processingLabel,processingError,autoOrganizePapers,reducedMotion,disabledSkills,toast,activePapers,notify,persist,newSpace,startQuestion,addPaper,addPdfPaper,bindDocument,search,cancel,loadSession,togglePaper,expand,ask,refreshDocuments,connectSelected,organizePapers,stopPaperProcessing,setAutoOrganize,editPaper,updatePaper,createDeck,saveDecks,deleteDeck,setReducedMotion,toggleSkill}
})
if(import.meta.hot)import.meta.hot.accept(acceptHMRUpdate(useResearchStore,import.meta.hot))
