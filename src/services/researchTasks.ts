import { backendApi,type BackendRun } from './backendApi'
import { useBackendStore } from '../stores/backend'
import { getDocument } from '../lib/storage'

/** Real model tasks use isolated, persisted conversations and the existing run executor. */
export async function runResearchTask(prompt:string,options:{title:string;skillId?:string;localDocumentIds?:string[];documentIds?:string[];signal?:AbortSignal;onStatus?:(run:BackendRun)=>void}){
  const backend=useBackendStore();await backend.init()
  if(!backend.user||!backend.model?.configured)throw new Error('请先登录并配置模型 API，再执行 AI 操作。')
  if(options.signal?.aborted)throw new Error('任务已取消。')
  if(prompt.length>8000)throw new Error('本次任务内容过长，请减少材料或选区。')
  const conversation=await backendApi.createConversation(options.title,backend.spaceId)
  const ids=new Set(options.documentIds||[])
  for(const localId of new Set(options.localDocumentIds||[])){
    const doc=await getDocument(localId);if(!doc)throw new Error('提供的本地 PDF 不存在，请重新上传。')
    const uploaded=await backendApi.uploadDocuments(conversation.id,[new File([doc.bytes],doc.name,{type:'application/pdf'})],options.signal)
    const item=uploaded.items[0];if(!item?.document)throw new Error(item?.message||'PDF 未能上传或解析。');ids.add(item.document.id)
  }
  if(ids.size>5)throw new Error('一次 AI 任务最多使用 5 份 PDF。')
  for(const id of ids)await backendApi.associateDocument(conversation.id,id)
  if(options.signal?.aborted)throw new Error('任务已取消。')
  const created=await backendApi.createRun(conversation.id,{prompt,skillId:options.skillId||'chat',documentIds:[...ids]},crypto.randomUUID())
  const abort=()=>{void backendApi.cancelRun(created.runId).catch(()=>{})};options.signal?.addEventListener('abort',abort,{once:true})
  try{
    for(;;){
      if(options.signal?.aborted)throw new Error('任务已取消。')
      const run=await backendApi.run(created.runId);options.onStatus?.(run)
      if(!['queued','running'].includes(run.status)){
        if(run.errorCode==='OUTPUT_TRUNCATED')throw new Error('模型回答达到输出上限，已有资料未被覆盖。请在模型设置中提高最大输出 Token 后重试。')
        if(run.errorCode==='EMPTY_ANSWER')throw new Error('模型返回了空回答（EMPTY_ANSWER），请单篇重试；若持续出现，请检查模型输出 Token 上限与供应商状态。')
        if(run.status!=='succeeded')throw new Error('模型任务未完整完成，请重试（'+(run.errorCode||run.status)+'）。')
        if(!run.answer.trim())throw new Error('模型没有返回内容。')
        return {answer:run.answer,runId:run.id,conversationId:conversation.id}
      }
      await new Promise<void>(resolve=>setTimeout(resolve,650))
    }
  }finally{options.signal?.removeEventListener('abort',abort)}
}
