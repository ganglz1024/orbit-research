import type {ReadingRequest} from '../lib/readerContext'
import { computed, ref, watch } from 'vue'
import { defineStore,acceptHMRUpdate } from 'pinia'
import { backendApi, BackendApiError, type BackendUser, type BackendSkill, type ModelConfiguration, type ResearchPreference, type BackendMessage, type BackendRun, type BackendDocument,type BackendConversation } from '../services/backendApi'
import {messageContext} from '../lib/composerContext'
import { readLocal, writeLocal, getDocument } from '../lib/storage'

export const errorText=(error:unknown)=>error instanceof TypeError?'无法连接后端，请确认 Spring Boot 已启动。':error instanceof Error?error.message:'请求未完成，请稍后重试。'
export const useBackendStore=defineStore('backend',()=>{
  const user=ref<BackendUser|null>(null),accountInitialized=ref<boolean|null>(null),loading=ref(false),error=ref('')
  const model=ref<ModelConfiguration|null>(null),skills=ref<BackendSkill[]>([])
  const preference=ref<ResearchPreference>({researchContext:'',defaultSkillId:'chat',allowSkillTools:false,assistantMode:'demo'})
  const messages=ref<BackendMessage[]>([]),conversationId=ref(''),run=ref<BackendRun|null>(null),admitting=ref(false)
  const channel=ref('chat')
  const spaceId=ref('default'),conversationHistory=ref<BackendConversation[]>([]),historyTotal=ref(0)
  const historyKey=()=>`orbit:conversation-index:${user.value?.id}`
  function rememberConversation(id:string){const index=readLocal<Record<string,string>>(historyKey(),{});index[id]=spaceId.value;try{writeLocal(historyKey(),index);writeLocal(cursorKey(),id)}catch{/* Backend data remains available. */}}
  const historyQuery=ref(''),historyScope=ref<'current'|'all'>('current'),historyState=ref<'active'|'archived'|'deleted'>('active'),historyLoading=ref(false),historyError=ref(''),historyPage=ref(-1)
  let historyVersion=0
  watch(()=>[historyQuery.value,historyScope.value,historyState.value,spaceId.value,user.value?.id],()=>{historyVersion++;conversationHistory.value=[];historyPage.value=-1;historyTotal.value=0},{flush:'sync'})
  const migratedOwners=new Set<string>()
  async function listHistory(page=0){
    if(!user.value)return
    const owner=user.value.id,space=spaceId.value,version=++historyVersion
    historyLoading.value=true;historyError.value=''
    try{
      if(!migratedOwners.has(owner)){
        const index=readLocal<Record<string,string>>(historyKey(),{})
        if(backendApi.associateConversation)await Promise.all(Object.entries(index).map(([id,workspace])=>backendApi.associateConversation(id,workspace).catch(()=>{})))
        migratedOwners.add(owner)
      }
      const data=await backendApi.conversations(page,20,{q:historyQuery.value,workspaceId:historyScope.value==='current'?space:undefined,state:historyState.value})
      if(version!==historyVersion||owner!==user.value?.id||space!==spaceId.value)return
      const index=readLocal<Record<string,string>>(historyKey(),{}),items=data.items.map(c=>({...c,workspaceId:c.workspaceId??index[c.id]}));conversationHistory.value=page?[...conversationHistory.value,...items]:items;historyTotal.value=data.total;historyPage.value=page
    }catch(e){if(version===historyVersion){historyError.value=errorText(e);throw e}}finally{if(version===historyVersion)historyLoading.value=false}
  }
  async function manageConversation(c:BackendConversation,action:string){
    if(admitting.value||attaching.value)throw new Error('请等待消息提交或材料处理完成。')
    const owner=user.value?.id,space=spaceId.value
    const result=await backendApi.manageConversation(c.id,action,c.revision)
    if(owner!==user.value?.id||space!==spaceId.value)return
    if(c.id===conversationId.value&&['delete','archive'].includes(action)){
      epoch++;stopStream();conversationId.value='';messages.value=[];attached.value=[];run.value=null;skillCalls.value=[];runError.value='';writeLocal(cursorKey(),'')
    }
    try{await listHistory()}catch{/* Mutation is committed; retain the separate list refresh error. */}return result
  }
  async function openConversation(id:string){if(busy.value||attaching.value)throw new Error('请先完成或停止当前任务。');const captured=++epoch,space=spaceId.value;const data=await backendApi.conversation(id);if(captured!==epoch||space!==spaceId.value)return;if(data.conversation?.workspaceId&&data.conversation.workspaceId!==space)throw new Error('此对话属于其他研究空间，请回到原空间打开。');stopStream();conversationId.value=id;messages.value=data.messages;attached.value=[];run.value=null;runError.value='';skillCalls.value=[];rememberConversation(id);const last=data.messages.at(-1);if(last?.role==='user'&&last.runId)await watchRun(await backendApi.run(last.runId))}
  let workspaceReady:Promise<void>|undefined
  const runError=ref(''),skillCalls=ref<string[]>([]),attached=ref<BackendDocument[]>([]),attaching=ref(false)
  const busy=computed(()=>admitting.value||!!run.value&&['queued','running'].includes(run.value.status))
  const modelMode=computed(()=>!!user.value&&!!model.value?.configured)
  const runnableSkills=computed(()=>skills.value.filter(s=>s.enabled&&s.status!=='not_implemented'))
  let initialized=false,initializing:Promise<void>|undefined,unsubscribe:(()=>void)|undefined,poll:ReturnType<typeof setInterval>|undefined,epoch=0
  let retry:{signature:string;key:string}|undefined
  const cursorKey=()=>`orbit:backend-conversation:${user.value?.id}:${spaceId.value}${channel.value==='chat'?'':':'+channel.value}`
  async function switchChannel(key:string){if(channel.value===key)return;epoch++;stopStream();channel.value=key;conversationId.value='';messages.value=[];attached.value=[];run.value=null;runError.value='';skillCalls.value=[];if(user.value)await restoreConversation()}
  function stopStream(){unsubscribe?.();unsubscribe=undefined;if(poll)clearInterval(poll);poll=undefined}
  async function refreshCapabilities(){const [m,s,p]=await Promise.all([backendApi.model(),backendApi.skills(),backendApi.preferences()]);model.value=m;skills.value=s.items;preference.value=p}
  function upsertAnswer(value:BackendRun){let message=messages.value.find(m=>m.runId===value.id&&m.role==='assistant');if(!message){messages.value.push({id:`answer-${value.id}`,runId:value.id,role:'assistant',content:'',createdAt:value.createdAt});message=messages.value.at(-1)!}return message}
  async function watchRun(value:BackendRun){
    stopStream();run.value=value;runError.value='';skillCalls.value=[]
    const message=upsertAnswer(value),currentEpoch=epoch
    if(!['queued','running'].includes(value.status)){message.content=value.answer;if(value.errorCode)runError.value=`任务未完成（${value.errorCode}），可保留已有内容后重试。`;return}
    message.content='';let after=0,checking=false
    async function check(){
      if(checking||currentEpoch!==epoch||!run.value)return
      checking=true
      try{const latest=await backendApi.run(value.id);if(currentEpoch!==epoch)return
        if(!['queued','running'].includes(latest.status)){run.value=latest;message.content=latest.answer;stopStream();if(latest.errorCode)runError.value=`任务未完成（${latest.errorCode}），已生成的内容会保留。`;try{const data=await backendApi.conversation(value.conversationId);if(currentEpoch===epoch)messages.value=data.messages}catch{/* Keep received text if snapshot refresh is unavailable. */}}
      }catch(e){if(currentEpoch===epoch)runError.value=errorText(e)}finally{checking=false}
    }
    unsubscribe=backendApi.subscribeRun(value.id,event=>{
      if(currentEpoch!==epoch||event.eventId<=after)return;after=event.eventId
      if(event.type==='answer_delta')message.content+=String(event.data.delta??'')
      if(event.type==='run_queued'){const execution={skill:{primary:{id:String(event.data.skillId||'chat'),name:String(event.data.skillName||'研究对话'),version:String(event.data.skillVersion||'')}},model:{model:String(event.data.model||''),requestedReasoning:String(event.data.requestedReasoning||'')}};message.execution=execution;const question=messages.value.find(m=>m.runId===value.id&&m.role==='user');if(question)question.execution={...question.execution,...execution}}
      if(event.type==='run_queued'&&event.data.skillId!=='chat')skillCalls.value.push('执行：'+String(event.data.skillName||event.data.skillId)+' · v'+String(event.data.skillVersion||''))
      if(event.type==='skill_installed'){skillCalls.value.push('已安装：'+String(event.data.name)+' · '+String(event.data.commitSha).slice(0,8));void refreshCapabilities()}
      if(event.type==='skill_called')skillCalls.value.push(`${event.data.name} · v${event.data.version}`)
      if(event.type==='warning')runError.value=String(event.data.message??'回答可能未完整。')
      if(['run_completed','run_failed','run_cancelled'].includes(event.type))void check()
    },()=>{void check()})
    poll=setInterval(()=>{void check()},2000) // Reconcile terminal status even if the final SSE frame is lost.
  }
  async function restoreConversation(){
    const saved=readLocal<string>(cursorKey(),spaceId.value==='default'&&channel.value==='chat'?readLocal<string>(`orbit:backend-conversation:${user.value?.id}`,''):'');if(!saved)return
    const capturedSpace=spaceId.value,capturedEpoch=epoch
    try{const data=await backendApi.conversation(saved);if(capturedSpace!==spaceId.value||capturedEpoch!==epoch)return;if(data.conversation?.archivedAt){writeLocal(cursorKey(),'');return}if(data.conversation?.workspaceId&&data.conversation.workspaceId!==capturedSpace){writeLocal(cursorKey(),'');return}conversationId.value=saved;messages.value=data.messages;rememberConversation(saved)
      const last=data.messages.at(-1);if(last?.runId&&last.role==='user')await watchRun(await backendApi.run(last.runId))
    }catch(e){if(e instanceof BackendApiError&&e.status===404)writeLocal(cursorKey(),'');else throw e}
  }
  async function init(force=false){
    if(initializing)return initializing;if(initialized&&!force)return
    initializing=(async()=>{loading.value=true;error.value=''
      try{accountInitialized.value=(await backendApi.setupStatus()).initialized
        try{user.value=await backendApi.me()}catch(e){if(e instanceof BackendApiError&&e.status===401){user.value=null;return}throw e}
        await refreshCapabilities();if(!conversationId.value)await restoreConversation()
      }catch(e){error.value=errorText(e)}finally{loading.value=false;initialized=true;initializing=undefined}
    })();return initializing
  }
  async function login(username:string,password:string,createAccount=false){
    if(createAccount){await backendApi.setup(username,password);accountInitialized.value=true}
    user.value=await backendApi.login(username,password);accountInitialized.value=true;initialized=true
    await refreshCapabilities();await restoreConversation()
  }
  async function logout(){await backendApi.logout();epoch++;stopStream();user.value=null;model.value=null;skills.value=[];preference.value={researchContext:'',defaultSkillId:'chat',allowSkillTools:false,assistantMode:'demo'};conversationId.value='';messages.value=[];attached.value=[];run.value=null}
  async function setPreference(p:ResearchPreference){preference.value=await backendApi.savePreferences(p)}
  async function switchWorkspace(id:string){if(id===spaceId.value)return;if(busy.value||attaching.value)throw new Error('请先完成或停止当前模型任务。');epoch++;historyVersion++;conversationHistory.value=[];historyPage.value=-1;stopStream();spaceId.value=id;conversationId.value='';messages.value=[];attached.value=[];run.value=null;if(user.value){workspaceReady=restoreConversation();try{await workspaceReady}finally{workspaceReady=undefined}}}
  async function ensureConversation(){if(workspaceReady)await workspaceReady;if(!conversationId.value){const captured=epoch,space=spaceId.value,c=await backendApi.createConversation('我的科研对话',spaceId.value);if(captured!==epoch||space!==spaceId.value)throw new Error('研究空间已改变，请在当前空间重试。');conversationId.value=c.id;rememberConversation(c.id)}return conversationId.value}
  async function newConversation(){if(busy.value||attaching.value)throw new Error('请先完成或停止当前任务。');if(!user.value)throw new Error('请先登录个人账户。');const captured=epoch,space=spaceId.value,owner=user.value.id,c=await backendApi.createConversation('我的科研对话',spaceId.value);if(captured!==epoch||space!==spaceId.value||owner!==user.value?.id)throw new Error('研究空间已改变，请在当前空间重试。');epoch++;stopStream();conversationId.value=c.id;messages.value=[];attached.value=[];run.value=null;skillCalls.value=[];runError.value='';rememberConversation(c.id)}
  async function attachLocal(id:string){
    if(attaching.value||busy.value)throw new Error('请等待当前模型或材料任务完成。')
    const existing=attached.value.find(d=>d.localId===id);if(existing)return existing
    if(attached.value.length>=5)throw new Error('每次最多选择 5 份材料。')
    attaching.value=true;const captured=epoch,space=spaceId.value
    try{const doc=await getDocument(id);if(!doc)throw new Error('本地文件不存在，请重新上传。')
      const result=await backendApi.uploadDocuments(await ensureConversation(),[new File([doc.bytes],doc.name,{type:'application/pdf'})]);const item=result.items[0]
      if(captured!==epoch||space!==spaceId.value)throw new Error('研究空间已改变，材料未绑定到新会话。')
      if(!item?.document)throw new Error(item?.message||'材料上传未完成。')
      if(!attached.value.some(d=>d.id===item.document!.id))attached.value.push({...item.document,localId:id})
      return attached.value.find(d=>d.id===item.document!.id)
    }finally{attaching.value=false}
  }
  async function send(prompt:string,options:{skillId?:string;installSkillUrl?:string;documentIds?:string[];reasoningEffort?:string;responseStyle?:string;readingContext?:ReadingRequest}={}){
    if(busy.value||attaching.value)return false
    if(!user.value||!model.value?.configured)throw new Error('请先在空间设置中登录并保存模型 API 配置。')
    const sendEpoch=epoch
    admitting.value=true;runError.value=''
    try{
      const id=await ensureConversation(),toolSkillIds=preference.value.allowSkillTools?runnableSkills.value.filter(s=>s.kind==='custom'&&s.id!==(options.skillId??'chat')).slice(0,8).map(s=>s.id):[]
      const body={prompt,skillId:options.skillId??'chat',documentIds:options.documentIds??attached.value.map(d=>d.id),toolSkillIds,installSkillUrl:options.installSkillUrl,reasoningEffort:options.reasoningEffort,responseStyle:options.responseStyle,readingContext:options.readingContext}
      if(!messages.value.length){try{const current=await backendApi.conversation(id);if(current.conversation.title==='我的科研对话')await backendApi.renameConversation(id,messageContext(prompt).text.trim().slice(0,48)||'科研对话',current.conversation.revision,true)}catch{/* Sending remains available if title update fails. */}}
      const signature=JSON.stringify([id,body]);if(retry?.signature!==signature)retry={signature,key:crypto.randomUUID()}
      const result=await backendApi.createRun(id,body,retry.key);retry=undefined
      if(sendEpoch!==epoch)return true // The accepted request stays in its original backend conversation.
      if(!messages.value.some(m=>m.runId===result.runId&&m.role==='user'))messages.value.push({id:crypto.randomUUID(),role:'user',content:prompt,runId:result.runId,createdAt:new Date().toISOString(),execution:options.readingContext?{reading:options.readingContext}:undefined})
      await watchRun(result.run);return true
    }finally{admitting.value=false}
  }
  async function cancel(){if(!run.value)return;const cancelled=await backendApi.cancelRun(run.value.id);epoch++;run.value=cancelled;upsertAnswer(cancelled).content=cancelled.answer;stopStream()}
  return{channel,switchChannel,historyQuery,historyScope,historyState,historyLoading,historyError,historyPage,manageConversation,spaceId,conversationHistory,historyTotal,listHistory,openConversation,user,accountInitialized,loading,error,model,skills,preference,messages,conversationId,run,runError,skillCalls,busy,admitting,modelMode,runnableSkills,attached,attaching,init,login,logout,refreshCapabilities,setPreference,switchWorkspace,ensureConversation,send,cancel,newConversation,attachLocal}
})

if(import.meta.hot)import.meta.hot.accept(acceptHMRUpdate(useBackendStore,import.meta.hot))
