<script setup lang="ts">
import type {ReaderComposerBridge} from '../lib/readerContext'
import CitationCard from './PdfCitationCard.vue'
import {computed,nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import {useResearchStore} from '../stores/research'
import {useBackendStore,errorText} from '../stores/backend'
import {useCopilotStore} from '../stores/copilot'
import {useComposerStore} from '../stores/composer'
import {useAutoGrowTextarea} from '../composables/useAutoGrowTextarea'
import {submitsPrompt,promptAfterSend} from '../lib/promptInput'
import {promptToken,parsePromptCommands,fuzzyCommandMatch,type PromptEntry,type PromptToken} from '../lib/promptCommands'
import {systemCommands,findSystemCommand,type SystemCommandId} from '../lib/systemCommands'
import {backendApi,type BackendCheckpoint} from '../services/backendApi'
import {taskIntent} from '../lib/taskIntent'
import {readLocal,writeLocal} from '../lib/storage'
import {contextPrompt,referenceKey,type ComposerReference,type MessageSource} from '../lib/composerContext'
import {paperEvidence} from '../lib/paperSources'
import {researchNoteMarkdown} from '../lib/researchNote'
import type {AnalysisPaper,ResearchNote} from '../types/copilot'
import Icon from './AppIcon.vue'
import CommandMenu from './PromptCommandMenu.vue'
import ConnectionControls from './AssistantConnectionControls.vue'
import ReasoningMenu from './ReasoningMenu.vue'
const props=defineProps<{notes:ResearchNote[];reader?:ReaderComposerBridge}>(),emit=defineEmits<{sent:[analysis:boolean];analysis:[];history:[];fresh:[]}>()
const research=useResearchStore(),backend=useBackendStore(),copilot=useCopilotStore(),composer=useComposerStore(),router=useRouter()
const draft=computed(()=>composer.forSpace(backend.user?.id||'guest',props.reader?.key||research.activeId||'default'))
const input=computed({get:()=>draft.value.text,set:value=>draft.value.text=value})
const textarea=ref<HTMLTextAreaElement>(),composing=ref(false),sending=ref(false),error=ref(''),notice=ref(''),panel=ref<'status'|'help'|'compact'|''>(''),checkpoint=ref<BackendCheckpoint|null>(null),checkpointConversation=ref(''),token=ref<PromptToken|null>(null),active=ref(0),addOpen=ref(false),modelOpen=ref(false),filter=ref('')
const responseStyle=ref(readLocal<string>('orbit:response-style','standard'))
const reasoning=ref(''),reasoningOpen=ref(false)
const currentCheckpoint=ref<BackendCheckpoint|null>(null)
const reasoningSupported=computed(()=>{try{return new URL(backend.model?.baseUrl||'').hostname==='api.deepseek.com'&&['deepseek-flash','deepseek-v4-pro'].includes(backend.model?.model||'')}catch{return false}})
const reasoningKey=computed(()=>`orbit:reasoning:${backend.user?.id||'guest'}:${backend.model?.baseUrl||''}:${backend.model?.model||''}`)
watch(()=>[backend.user?.id,backend.model?.baseUrl,backend.model?.model],()=>{const saved=readLocal<string>(reasoningKey.value,'');reasoning.value=reasoningSupported.value&&['none','low','high','max'].includes(saved)?saved:'';reasoningOpen.value=false},{immediate:true})
function setReasoning(value:string){reasoning.value=value;try{writeLocal(reasoningKey.value,value)}catch{/* Current selection remains available for this session. */}reasoningOpen.value=false}
let disposed=false,uploadIntent:{ids:Set<string>;draft:typeof draft.value}|undefined
function upload(){uploadIntent={ids:new Set(research.documents.map(d=>d.id)),draft:draft.value};research.uploadOpen=true;addOpen.value=false}
watch(()=>research.documents.map(d=>d.id).join(','),()=>{if(!uploadIntent)return;const request=uploadIntent;for(const doc of research.documents.filter(d=>!request.ids.has(d.id))){if(!request.draft.references.some(r=>r.kind==='document'&&r.id===doc.id))request.draft.references.push({id:doc.id,name:doc.name,kind:'document',description:'本地 PDF · 发送时解析'});request.ids.add(doc.id)}})
watch(()=>research.completedUpload,batch=>{if(!uploadIntent||!batch)return;const target=uploadIntent.draft;for(const id of batch.ids){const doc=research.documents.find(d=>d.id===id);if(doc&&!target.references.some(r=>r.kind==='document'&&r.id===id))target.references.push({id,name:doc.name,kind:'document',description:'本地 PDF · 发送时解析'})}})
watch(()=>research.uploadOpen,value=>{if(!value)uploadIntent=undefined})
useAutoGrowTextarea(textarea,input)
const busy=computed(()=>backend.busy||backend.attaching||copilot.busy||sending.value)
const academicAliases:Record<string,string[]>={ars_research:['ars-research','deep-research'],ars_write:['ars-write','academic-paper'],ars_review:['ars-review','academic-paper-reviewer'],ars_pipeline:['ars-pipeline','ars-plan','academic-pipeline'],ars_screen:['ars-screen','sr-screener']}
const entries=computed<PromptEntry[]>(()=>[
  ...systemCommands.map(c=>({...c,kind:'command' as const,aliases:[c.id]})),
  {id:'chat',name:'通用研究对话',kind:'skill' as const,aliases:['chat'],description:'当前模型 · 普通提问'},
  ...backend.runnableSkills.map(s=>({id:s.id,name:s.name,kind:'skill' as const,aliases:s.id==='paper_read'?['read']:s.id==='paper_compare'?['compare']:academicAliases[s.id]||[],description:s.description})),
  {id:'research_plan',name:'规划研究路线',kind:'skill' as const,aliases:['plan'],description:'已实现的模型任务 · 阅读路线建议'},
  {id:'research_gaps',name:'研究空白辅助分析',kind:'skill' as const,aliases:['gaps'],description:'已实现的模型任务 · 至少两篇资料 · 待验证推断'},
  {id:'ppt_generate',name:'生成科研汇报',kind:'skill' as const,description:'已实现的模型汇报任务 · 生成可编辑成果'},
  {id:'keywords',name:'提取检索关键词',kind:'skill' as const,description:'模型建议，非真实检索'},
  ...(props.reader?[]:research.activePapers.map(p=>({id:p.id,name:p.title,kind:'paper' as const,description:p.localDocumentId?'题录 · 本地 PDF 可另行引用':'仅元数据 · 未绑定 PDF'}))),
  ...(props.reader?[]:research.documents.map(d=>({id:d.id,name:d.name,kind:'document' as const,description:d.pageCount+' 页 · 本地文件，发送时解析'}))),
  ...(props.reader?.mentions||[]),
  ...props.notes.map(n=>({id:n.id,name:n.title,kind:'note' as const,description:'空间笔记 v'+n.revision+' · 用户保存的内容'}))
].filter(e=>!props.reader||e.kind!=='skill'||e.id==='chat'||e.id==='paper_read'||e.id.startsWith('ars_')||backend.skills.some(s=>s.id===e.id&&s.kind==='custom')).filter((e,i,all)=>all.findIndex(v=>v.kind===e.kind&&v.id===e.id)===i))
const options=computed(()=>{if(!token.value)return[];const q=token.value.query.toLowerCase();return entries.value.filter(e=>(token.value!.kind==='slash'?e.kind==='skill'||e.kind==='command':e.kind!=='skill'&&e.kind!=='command')&&(!filter.value||e.kind===filter.value)&&fuzzyCommandMatch(q,e.name+' '+e.id+' '+(e.aliases?.join(' ')||''))).slice(0,30)})
const availablePdfs=computed(()=>props.reader?[]:research.activePapers.filter(p=>p.localDocumentId&&(p.id===research.selectedPaperId||draft.value.references.some(r=>r.kind==='paper'&&r.id===p.id))&&!draft.value.references.some(r=>r.kind==='document'&&r.id===p.localDocumentId)&&!backend.attached.some(d=>d.localId===p.localDocumentId)))
function referencePdf(id:string,name:string){draft.value.references.push({id,name,kind:'document',description:'用户确认本轮引用 · 发送时提供片段'})}
const visibleReferences=computed(()=>draft.value.references.filter(r=>r.kind!=='document'||!backend.attached.some(d=>d.localId===r.id)))
const statusPapers=computed(()=>{const explicit=draft.value.references.filter(r=>r.kind==='paper').map(r=>r.id);return new Set(draft.value.references.length||draft.value.ignoreCanvas?explicit:copilot.sources.filter(s=>research.paperById(s.id)).map(s=>s.id)).size})
function removePdf(id:string){const doc=backend.attached.find(d=>d.id===id);backend.attached=backend.attached.filter(d=>d.id!==id);draft.value.references=draft.value.references.filter(r=>r.kind!=='document'||r.id!==doc?.localId)}
const selectedSkill=computed(()=>entries.value.find(e=>e.kind==='skill'&&e.id===draft.value.skillId))
function updateToken(){if(composing.value)return;filter.value='';token.value=promptToken(input.value,textarea.value?.selectionStart||0);active.value=0}
async function trigger(kind:'slash'|'mention',only=''){addOpen.value=false;modelOpen.value=false;filter.value=only;const caret=textarea.value?.selectionStart??input.value.length,mark=kind==='slash'?'/':'@',prefix=caret&&/\S/.test(input.value[caret-1]!)?' ':'';input.value=input.value.slice(0,caret)+prefix+mark+input.value.slice(caret);const end=caret+prefix.length+1;token.value={kind,query:'',start:end-1,end};active.value=0;await nextTick();textarea.value?.focus();textarea.value?.setSelectionRange(end,end)}
async function choose(entry:PromptEntry){
  if(!token.value)return
  if(entry.kind==='command'){
    const remaining=input.value.slice(0,token.value.start)+input.value.slice(token.value.end)
    if(remaining.trim()){error.value='请先保留或发送草稿，再单独执行命令。';token.value=null;return}
    token.value=null;await executeCommand(entry.id as SystemCommandId);return
  }
  input.value=input.value.slice(0,token.value.start)+input.value.slice(token.value.end)
  if(entry.kind==='skill')draft.value.skillId=entry.id
  else if(!draft.value.references.some(r=>referenceKey(r)===referenceKey(entry as ComposerReference))){const note=props.notes.find(n=>n.id===entry.id);draft.value.references.push({id:entry.id,kind:entry.kind,name:entry.name,description:entry.description,...(entry.kind==='note'&&note?{body:researchNoteMarkdown(note).slice(0,1800),revision:note.revision}:{})})}
  token.value=null;filter.value='';await nextTick();textarea.value?.focus()
}
async function executeCommand(id:SystemCommandId){
  const command=systemCommands.find(c=>c.id===id);if(!command)return
  const original=input.value,sourceDraft=draft.value
  error.value='';notice.value=''
  if(!command.available){error.value=command.description+'，当前不会发送给模型。';return}
  try{
    if(id==='clear'){
      if(busy.value)throw new Error('请先完成或停止当前任务，草稿已保留。')
      if(backend.attached.some(d=>!d.localId))throw new Error('当前有无法在新对话中恢复的 PDF 引用，请先移除或完成使用。')
      const selected=backend.attached.map(d=>({id:d.localId!,name:d.name,kind:'document' as const,description:'本地 PDF · 发送时重新解析'}))
      sending.value=true;try{await (props.reader?props.reader.fresh():backend.newConversation())}finally{sending.value=false};for(const item of selected)if(!draft.value.references.some(r=>referenceKey(r)===referenceKey(item)))draft.value.references.push(item)
      emit('fresh');notice.value='已开始新对话，原对话已保存在历史记录中。'
    }else if(id==='compact'){
      if(!backend.conversationId)throw new Error('当前没有可压缩的对话。')
      const conversation=backend.conversationId,space=backend.spaceId;sending.value=true
      try{const result=await backendApi.compactPreview(conversation);if(conversation!==backend.conversationId||space!==backend.spaceId)throw new Error('研究空间已改变，请在当前会话重试。');checkpoint.value=result;checkpointConversation.value=conversation;panel.value='compact'}finally{sending.value=false}
    }else if(id==='history')emit('history')
    else if(id==='model'){modelOpen.value=true;panel.value=''}
    else if(id==='reasoning'){reasoningOpen.value=true;modelOpen.value=false;addOpen.value=false;panel.value=''}
    else if(id==='status'||id==='help'){if(id==='status'&&backend.conversationId){const result=await backendApi.compactStatus(backend.conversationId);currentCheckpoint.value=result.checkpoint||null}else currentCheckpoint.value=null;panel.value=id}
    else if(id==='export'){
      if(!backend.messages.length)throw new Error('当前对话没有可导出的消息。')
      const text=backend.messages.map(m=>`## ${m.role==='user'?'用户':'Orbit'}\n\n${m.content}`).join('\n\n')
      const url=URL.createObjectURL(new Blob([text],{type:'text/markdown;charset=utf-8'})),a=document.createElement('a');a.href=url;a.download='orbit-conversation.md';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
      notice.value='已发起对话导出。'
    }
    if(sourceDraft.text===original)sourceDraft.text=''
  }catch(e){error.value=errorText(e)}
}
async function applyCheckpoint(){if(!checkpoint.value||backend.conversationId!==checkpointConversation.value){error.value='会话已改变，请重新生成摘要。';return}try{sending.value=true;await backendApi.compactApply(checkpointConversation.value,checkpoint.value.id);notice.value='摘要已应用，原始对话仍保留。';panel.value='';checkpoint.value=null}catch(e){error.value=errorText(e)}finally{sending.value=false}}
function dismiss(e:KeyboardEvent){if(e.key==='Escape'&&(addOpen.value||modelOpen.value||token.value||panel.value)){e.preventDefault();e.stopPropagation();addOpen.value=false;modelOpen.value=false;token.value=null;panel.value='';textarea.value?.focus()}}
function keys(e:KeyboardEvent){if(e.isComposing||composing.value||e.keyCode===229)return;if(token.value){if(e.key==='Escape'){e.preventDefault();e.stopPropagation();token.value=null;return}if(['ArrowDown','ArrowUp'].includes(e.key)){e.preventDefault();active.value=(active.value+(e.key==='ArrowDown'?1:-1)+Math.max(options.value.length,1))%Math.max(options.value.length,1);return}if(['Enter','Tab'].includes(e.key)&&options.value.length){e.preventDefault();void choose(options.value[active.value]!);return}}if(submitsPrompt(e,composing.value)){e.preventDefault();void send()}}
async function send(){
  if(busy.value||composing.value||(!input.value.trim()&&(!draft.value.skillId||draft.value.skillId==='chat')))return
  const system=findSystemCommand(input.value);if(system){await executeCommand(system.id);return}
  const submitted=input.value,sourceDraft=draft.value,space=research.activeId,owner=backend.user?.id,readerAtSubmit=props.reader
  const readingSnapshot=readerAtSubmit?JSON.parse(JSON.stringify(readerAtSubmit.citation.source)):undefined
  sending.value=true;error.value='';token.value=null;addOpen.value=false;modelOpen.value=false
  try{
    await backend.init();if(!backend.modelMode)throw new Error('请先登录并配置模型 API。')
    const parsed=parsePromptCommands(submitted,entries.value)
    if(readerAtSubmit){
      const references=[...sourceDraft.references,...parsed.mentions] as ComposerReference[]
      const accepted=await readerAtSubmit.send({readingContext:readingSnapshot,text:parsed.prompt,skillId:parsed.skill?.id||sourceDraft.skillId||'chat',references,reasoningEffort:reasoning.value||undefined,responseStyle:responseStyle.value})
      if(accepted){if(backend.run)sourceDraft.pending={runId:backend.run.id,text:submitted,references:JSON.parse(JSON.stringify(sourceDraft.references)),skillId:sourceDraft.skillId,reading:readingSnapshot};sourceDraft.text=promptAfterSend(sourceDraft.text,submitted,true);sourceDraft.skillId='';sourceDraft.references=[];emit('sent',false)}
      return
    }
    const skill=taskIntent(parsed.prompt,parsed.skill?.id||sourceDraft.skillId)
    if(parsed.skill)sourceDraft.skillId=parsed.skill.id
    const skillName=entries.value.find(e=>e.kind==='skill'&&e.id===skill)?.name||skill
    const references=[...sourceDraft.references,...parsed.mentions.filter(e=>e.kind!=='skill').map(e=>{const note=props.notes.find(n=>n.id===e.id);return {...e,...(e.kind==='note'&&note?{body:researchNoteMarkdown(note).slice(0,1800),revision:note.revision}:{})} as ComposerReference})]
    const unique=references.filter((r,i)=>references.findIndex(v=>referenceKey(v)===referenceKey(r))===i)
    const paperIds=unique.filter(r=>r.kind==='paper').map(r=>r.id)
    if(!unique.length&&!sourceDraft.ignoreCanvas)paperIds.push(...copilot.sources.filter(s=>research.paperById(s.id)).map(s=>s.id))
    const papers=[...new Set(paperIds)].map(research.paperById).filter(p=>!!p)
    if(unique.some(r=>r.kind==='paper'&&!research.paperById(r.id)))throw new Error('引用的论文已移除，请调整引用后发送。')
    const organization=['paper_metadata','paper_relations'].includes(skill)
    const localIds=unique.filter(r=>r.kind==='document').map(r=>r.id)
    if(!organization&&(papers.length>5||new Set([...localIds,...backend.attached.map(d=>d.localId||d.id)]).size>5))throw new Error('一次对话或分析最多提供 5 篇论文 / PDF。')
    const extraDocuments=new Set([...localIds,...backend.attached.map(d=>d.localId||d.id)]);for(const paper of papers){if(paper.localDocumentId)extraDocuments.delete(paper.localDocumentId);if(paper.backendDocumentId)extraDocuments.delete(paper.backendDocumentId);const bound=backend.attached.find(d=>d.id===copilot.bindings[paper.id]);if(bound)extraDocuments.delete(bound.localId||bound.id)}
    const sourceCount=papers.length+extraDocuments.size
    if(['paper_read','paper_compare'].includes(skill)&&sourceCount>5)throw new Error('结构化分析最多提供 5 篇来源，请减少引用。')
    if(skill==='paper_read'&&sourceCount!==1)throw new Error('论文精读需要一篇明确选择的论文或 PDF，请调整引用。')
    if(skill==='paper_compare'&&sourceCount<2)throw new Error('论文比较至少需要两篇明确选择的论文或 PDF。')
    if(['paper_read','paper_compare','research_plan','research_gaps','paper_metadata','paper_relations','ppt_generate'].includes(skill)&&unique.some(r=>r.kind==='note'))throw new Error('该结构化任务目前接受论文/PDF；笔记请使用通用对话引用，避免把笔记当成论文原文。')
    if(['paper_read','paper_compare'].includes(skill)&&parsed.prompt.length>1500)throw new Error('精读与对比的补充要求最多 1500 字符，请精简后重试。')
    const academicTask=skill.startsWith('ars_')
    if(academicTask&&!parsed.prompt.trim()&&!research.question.trim()&&!unique.length)throw new Error('请输入研究问题或引用材料，再调用该研究 Skill。')
    const text=parsed.prompt.trim()||({'paper_read':'请解读本次提供的论文。','paper_compare':'请比较本次提供的论文。','paper_metadata':'整理论文卡片','paper_relations':'分析论文联系','ars_research':'请围绕当前研究问题梳理研究计划：'+research.question,'ars_write':'请根据当前研究问题和资料拟定学术写作提纲：'+research.question,'ars_review':'请评阅本次明确提供的稿件或材料，并标注证据范围。','ars_pipeline':'请规划当前研究的下一阶段：'+research.question,'ars_screen':'请先检查纳排标准与本次提供的记录，缺少标准时只拟定待确认的筛选方案。'}[skill]||'')
    // Validate the note/metadata budget before uploading a local file.
    const sources:MessageSource[]=[...papers.map(p=>({id:p.id,kind:'paper',name:p.title,scope:'提供的元数据；不等于已读全文',body:JSON.stringify({...paperEvidence(p),metadata:p.summary.slice(0,500)})})),...unique.filter(r=>r.kind==='note').map(n=>({id:n.id,kind:'note',name:n.name,scope:'用户保存的笔记快照 v'+n.revision+'；最多引用1800字符',body:n.body||''}))]
    if(!organization)contextPrompt(text,{version:1,skill,skillName,sources})
    if(!research.activeId)research.newSpace()
    const taskSpace=research.activeId,taskOwner=backend.user?.id
    await nextTick();await backend.switchWorkspace(taskSpace||'default')
    for(const id of organization?[]:localIds){const doc=await backend.attachLocal(id);if(doc)research.bindDocument(id,doc.id)}
    if((space&&research.activeId!==space)||(owner&&backend.user?.id!==owner))throw new Error('研究空间或账户已改变，草稿已保留。')
    const assertScope=()=>{if(research.activeId!==taskSpace||backend.user?.id!==taskOwner)throw new Error('研究空间已改变，草稿已保留。')}
    const analysisSources:AnalysisPaper[]=[...papers.map(p=>({...paperEvidence(p),documentId:copilot.bindings[p.id]||p.backendDocumentId})),...backend.attached.filter(d=>!papers.some(p=>copilot.bindings[p.id]===d.id||p.backendDocumentId===d.id||p.localDocumentId===d.localId)).map(d=>({id:'doc_'+d.id,title:d.name,documentId:d.id}))]
    let accepted=false
    if(['paper_metadata','paper_relations'].includes(skill)){const ids=[...papers.map(p=>p.id)];for(const id of localIds){const doc=research.documents.find(d=>d.id===id);if(doc)ids.push(research.addPdfPaper(doc).id)}accepted=skill==='paper_metadata'?await research.organizePapers(ids.length?ids:research.visibleIds):await research.connectSelected(ids.length?ids:undefined)}
    else if(skill==='ppt_generate'){const ids=papers.map(p=>p.id);for(const id of localIds){const doc=research.documents.find(d=>d.id===id);if(doc)ids.push(research.addPdfPaper(doc).id)}if(ids.length)research.selectedIds=[...new Set(ids)];const deck=await research.createDeck(text,{documentIds:backend.attached.map(d=>d.id),localDocumentIds:localIds});accepted=!!deck;if(deck)await router.push('/artifacts/'+deck.id)}
    else if(['paper_read','paper_compare'].includes(skill)){
      if(papers.length){research.selectedPaperId=null;research.selectedRelation=null;research.selectedIds=papers.map(p=>p.id);copilot.questionMode=false;copilot.sourceMode='papers'}else{copilot.questionMode=false;copilot.sourceMode='materials'}
      copilot.focus=submitted.trim()?parsed.prompt:''
      await nextTick();await copilot.analyze(skill==='paper_read'?'plain':'compare',analysisSources);accepted=!copilot.error;if(accepted&&copilot.current)sourceDraft.pending={kind:'analysis',runId:copilot.current.id,text:submitted,references:JSON.parse(JSON.stringify(unique)),skillId:sourceDraft.skillId};emit('analysis')
    }else if(['research_plan','research_gaps'].includes(skill)){
      const topic=submitted.trim()?parsed.prompt:research.question;if(!topic)throw new Error('请输入研究主题，再执行此任务。')
      research.startQuestion(topic);if(papers.length){research.selectedPaperId=null;research.selectedRelation=null;research.selectedIds=papers.map(p=>p.id);copilot.questionMode=false;copilot.sourceMode='papers'}
      await nextTick();await copilot.assist(skill==='research_plan'?'plan':'gaps',analysisSources.map(source=>({...source,documentId:backend.attached.some(d=>d.id===source.documentId)?source.documentId:copilot.bindings[source.id]})),submitted.trim()?parsed.prompt:'');accepted=!!copilot.taskResult&&!copilot.error;emit('analysis')
    }else{
      if(!text&&skill!=='chat')throw new Error('请输入任务要求。')
      if(skill==='keywords'&&!text&&!research.question.trim())throw new Error('请输入研究主题，再提取检索关键词。')
      let prompt=skill==='keywords'?'请提取中英文检索关键词并说明用法；这些是检索建议，不能声称已经检索验证。研究主题：'+(text||research.question):text
      if(!prompt.trim())throw new Error('请输入研究问题。')
      const docs=backend.attached.map(d=>({id:d.id,kind:'document',name:d.name,versionId:d.versionId,hash:d.hash,scope:d.parseStatus==='text_ready'?'已解析片段；未保证覆盖全文':d.parseStatus==='text_truncated'?'已解析的有限片段':'缺少可用文字，不能全文分析'}))
      prompt=contextPrompt(prompt,{version:1,skill,skillName,sources:[...sources,...docs]});assertScope()
      // A chat task must not become the research-space topic.
      const github=/https:\/\/github\.com\/[^\s<>]+/.exec(parsed.prompt)?.[0]?.replace(/[，。；）)]+$/,'')
      const installSkillUrl=github&&/(安装|导入|install|import).{0,30}(skill|技能)|(skill|技能).{0,30}(安装|导入|install|import)/i.test(submitted)?github:undefined
      accepted=await backend.send(prompt,{skillId:skill==='keywords'?'chat':skill,documentIds:backend.attached.map(d=>d.id),installSkillUrl,reasoningEffort:reasoning.value||undefined,responseStyle:responseStyle.value})
      if(accepted&&backend.run)sourceDraft.pending={runId:backend.run.id,text:submitted,references:JSON.parse(JSON.stringify([...unique,...papers.map(p=>({id:p.id,name:p.title,kind:'paper'})),...backend.attached.filter(d=>d.localId).map(d=>({id:d.localId!,kind:'document',name:d.name}))])),skillId:sourceDraft.skillId}
    }
    if(accepted){sourceDraft.skillId='';sourceDraft.text=promptAfterSend(sourceDraft.text,submitted,true);sourceDraft.references=sourceDraft.references.filter(r=>!unique.some(x=>referenceKey(x)===referenceKey(r)));emit('sent',['paper_read','paper_compare','research_plan','research_gaps'].includes(skill))}else if(!error.value)error.value=copilot.error||research.processingError||'任务尚未提交，请重试。'
  }catch(e){if(!disposed)error.value=errorText(e)+' 草稿和引用已保留。'}finally{if(!disposed)sending.value=false}
}
async function stop(){try{if(backend.busy)await backend.cancel();else if(copilot.taskBusy)copilot.stopTask();else if(copilot.current&&copilot.state==='loading')await copilot.cancel();else research.stopPaperProcessing()}catch(e){error.value=errorText(e)}}
watch(()=>[research.activeId,backend.user?.id],()=>{token.value=null;addOpen.value=false;modelOpen.value=false;error.value=''})
function recover(){const failed=draft.value.failed;if(!failed)return;draft.value.text=[draft.value.text,failed.text].filter(Boolean).join('\n');for(const ref of failed.references)if(!draft.value.references.some(r=>referenceKey(r)===referenceKey(ref)))draft.value.references.push(ref);if(!draft.value.skillId)draft.value.skillId=failed.skillId;if(failed.reading)props.reader?.restoreCitation(failed.reading);draft.value.failed=undefined;textarea.value?.focus()}
watch(()=>[backend.run?.id,backend.run?.status,copilot.current?.id,copilot.state,draft.value.pending?.runId],()=>{
  const pending=draft.value.pending;if(!pending)return
  const id=pending.kind==='analysis'?copilot.current?.id:backend.run?.id,status=pending.kind==='analysis'?copilot.state:backend.run?.status
  if(id!==pending.runId||!status||['queued','running','loading'].includes(status))return
  draft.value.pending=undefined
  if(!['succeeded','success','insufficient_data'].includes(status)){draft.value.failed={text:pending.text,references:pending.references,skillId:pending.skillId,reading:pending.reading};if(!draft.value.text.trim())recover();error.value='模型任务未完整完成，原始问题与引用已保留；可调整后重试。'}
},{flush:'post'})
watch(input,()=>error.value='')
watch(()=>[addOpen.value,modelOpen.value,!!token.value],([add,model,command])=>{if(add||model||command)reasoningOpen.value=false})
watch(reasoningOpen,value=>{if(value){addOpen.value=false;modelOpen.value=false;token.value=null}})
function outsideComposer(e:PointerEvent){if(!(e.target as Element)?.closest?.('.research-composer-wrap')){addOpen.value=false;modelOpen.value=false;token.value=null;panel.value=''}}
onMounted(()=>document.addEventListener('pointerdown',outsideComposer))
defineExpose({focus:()=>textarea.value?.focus(),submit:(text:string)=>{if(input.value.trim()){input.value+='\n'+text;textarea.value?.focus();return}input.value=text;void send()}})
onBeforeUnmount(()=>{disposed=true;document.removeEventListener('pointerdown',outsideComposer)})
</script>
<template><div class="research-composer-wrap"><form class="assistant-compose unified-composer" :aria-busy="sending||backend.busy" @submit.prevent="send" @keydown="dismiss">
  <div v-if="availablePdfs.length" class="composer-status">检测到所选论文的本地 PDF。<button v-for="p in availablePdfs" :key="p.id" type="button" class="text-button" @click="referencePdf(p.localDocumentId!,p.title)">本轮引用 PDF：{{p.title}}</button></div>
  <CommandMenu v-if="token" :entries="options" :active="active" :kind="token.kind" @choose="choose"/>

  <div v-if="panel" class="composer-popover" role="region" :aria-label="panel==='status'?'会话状态':panel==='compact'?'压缩摘要':'命令说明'">
    <button type="button" class="text-button" @click="panel=''">关闭</button>
    <template v-if="panel==='status'"><strong>当前会话状态</strong><p>模型：{{backend.model?.model||'未配置'}} · {{backend.modelMode?'已配置，连接状态未实时验证':'不可用'}} · 思考请求：{{reasoning||'供应商默认'}}</p><p>研究空间：{{research.question||'未命名'}} · 消息 {{backend.messages.length}} 条</p><p>当前来源：{{statusPapers}} 篇论文，{{backend.attached.length}} 份已绑定 PDF；{{draft.references.filter(r=>r.kind==='document').length}} 份本地 PDF 待处理。</p><p>压缩检查点：{{currentCheckpoint?currentCheckpoint.createdAt:'尚无'}}</p><p>Token 用量和上下文窗口：暂无可靠数据。</p></template>
    <template v-else-if="panel==='compact'&&checkpoint"><strong>压缩摘要预览</strong><p>覆盖 {{checkpoint.coveredIds.length}} 条历史消息，最近 4 条保留。原始消息和来源不删除；摘要仅作背景，核验时请重新引用原文。</p><p style="white-space:pre-wrap;max-height:200px;overflow:auto">{{checkpoint.summary}}</p><button type="button" :disabled="sending||busy" @click="applyCheckpoint">确认应用摘要</button></template>
    <template v-else><strong>命令说明</strong><p v-for="command in systemCommands" :key="command.id">{{command.name}} · {{command.description}}</p><p>输入 / 可查看已注册的科研 Skills；选择后按所需来源执行。</p></template>
  </div>
  <div v-if="addOpen" class="composer-popover" role="group" aria-label="添加研究资料"><button type="button" @click="upload"><Icon name="Upload" :size="15"/>上传 PDF</button><button type="button" @click="trigger('mention','paper')">{{reader?'引用当前论文 · 题录':'引用画布论文 · 题录'}}</button><button v-if="!reader" type="button" @click="trigger('mention','document')">引用已有研究材料 · PDF</button><button v-else type="button" @click="reader.referenceSelection();addOpen=false">引用当前 PDF 选区</button><button type="button" @click="trigger('mention','note')">引用空间笔记</button></div>
  <div v-if="modelOpen" class="composer-popover composer-model-popover" role="group" aria-label="模型与高级设置"><ConnectionControls composer/><label>回答详细程度<select v-model="responseStyle" @change="writeLocal('orbit:response-style',responseStyle)"><option value="concise">简洁</option><option value="standard">标准</option><option value="detailed">深入</option></select></label><small>{{backend.modelMode?'已配置 · 连接状态可在设置中测试':'尚未配置模型'}}</small><p>使用已保存的模型配置。更换模型请打开设置。</p><RouterLink to="/settings#model">配置或更换模型 ↗</RouterLink></div>
  <div v-if="selectedSkill||draft.references.length||backend.attached.length" class="composer-reference-tags" aria-label="本次操作与资料">
    <span v-if="selectedSkill" class="composer-tag skill"><span>/ {{selectedSkill.name}}</span><button type="button" aria-label="取消本次 Skill" @click="draft.skillId=''">×</button></span>
    <span v-for="ref in visibleReferences" :key="referenceKey(ref)" class="composer-tag" :title="ref.name+' · '+(ref.description||'本次明确引用')"><span>{{ref.kind==='note'?'笔记':ref.kind==='document'?'PDF · 待提供':'题录'}} · {{ref.name}}</span><button type="button" :aria-label="'移除引用 '+ref.name" @click="draft.references=draft.references.filter(r=>referenceKey(r)!==referenceKey(ref))">×</button></span>
    <span v-for="doc in backend.attached" :key="doc.id" class="composer-tag" :title="doc.name+' · '+doc.parseStatus"><span>PDF · {{doc.parseStatus==='text_ready'?'已解析片段':doc.parseStatus==='text_truncated'?'有限片段':'证据不足'}} · {{doc.name}}</span><button type="button" :disabled="busy" :aria-label="'移除 PDF 引用 '+doc.name" @click="removePdf(doc.id)">×</button></span>
  </div>
  <div v-if="reader?.citation.source.evidence.length" class="composer-pdf-citations" aria-label="待发送原文引用"><CitationCard v-for="block in reader.citation.source.evidence" :key="reader.citation.source.documentVersion+block.chunkId+block.quote" :source="reader.citation.source" :block="block" :title="reader.citation.title" removable @remove="reader.removeCitation();textarea?.focus()" @locate="reader.locateCitation($event)"/></div>
  <label for="assistant-question" class="visually-hidden">研究问题与持续提问</label><textarea id="assistant-question" ref="textarea" v-model="input" rows="2" maxlength="6000" placeholder="提出研究问题，或用 @ 引用资料、/ 调用 Skill" :aria-invalid="!!error" :aria-describedby="error?'composer-error':'composer-scope'" :aria-controls="token?'prompt-command-menu':undefined" :aria-activedescendant="token&&options.length?'prompt-option-'+active:undefined" aria-autocomplete="list" @input="updateToken" @click="updateToken" @keyup.left="updateToken" @keyup.right="updateToken" @compositionstart="composing=true;token=null" @compositionend="composing=false;updateToken()" @keydown="keys"/>
  <button v-if="draft.failed" type="button" class="text-button" @click="recover">将失败任务的草稿与引用追加回来</button><p v-if="backend.attaching||sending" role="status" class="composer-status">{{backend.attaching?'正在上传并解析明确引用的 PDF…':'正在处理请求…'}}</p><p v-if="notice" role="status" class="composer-status">{{notice}}</p><p v-if="error" id="composer-error" role="alert" class="compose-error">{{error}}</p>
  <div class="compose-toolbar"><button type="button" class="icon-button" aria-label="添加研究资料" :aria-expanded="addOpen" @click="addOpen=!addOpen;modelOpen=false;token=null"><Icon name="Plus" :size="18"/></button><button type="button" class="composer-command-button" aria-label="引用论文、PDF 或笔记" @click="trigger('mention')">@</button><button type="button" class="composer-command-button" aria-label="选择科研 Skill" @click="trigger('slash')">/</button><ReasoningMenu :model-value="reasoning" v-model:open="reasoningOpen" :supported="reasoningSupported" @select="setReasoning"/><button type="button" class="composer-model-button" aria-label="模型与高级设置" :aria-expanded="modelOpen" @click="modelOpen=!modelOpen;addOpen=false;token=null"><span>{{backend.modelMode?backend.model!.model:'连接模型'}}</span><Icon name="ChevronDown" :size="12"/></button><button v-if="backend.busy||copilot.busy" type="button" class="send-button" aria-label="停止 AI 任务" :disabled="backend.admitting" @click="stop"><Icon name="Square" :size="13"/></button><button v-else type="submit" class="send-button" aria-label="发送研究问题" :disabled="(!input.trim()&&(!draft.skillId||draft.skillId==='chat'))||sending||composing||backend.attaching"><Icon :name="sending?'LoaderCircle':'ArrowUp'" :class="{spin:sending}" :size="17"/></button></div>
</form><p id="composer-scope" class="composer-scope-hint">明确引用的资料会发送至当前模型 · 片段不等于全文</p></div></template>

<style scoped>.composer-pdf-citations{display:block;width:100%;min-width:0;overflow:visible;margin-bottom:8px}</style>
