export interface ComposerReference {id:string;kind:'paper'|'document'|'note';name:string;description?:string;body?:string;revision?:number}
export interface MessageSource {id:string;kind:string;name:string;scope:string;versionId?:string;hash?:string;body?:string}
export interface MessageContext {taskInstructions?:string;version:1;skill:string;skillName?:string;sources:MessageSource[]}
export const CONTEXT_MARKER='\n\nORBIT_CONTEXT_V1\n'
export function contextPrompt(text:string,context:MessageContext){
  const result=text+CONTEXT_MARKER+JSON.stringify(context)
  if(result.length>8000)throw new Error('本次问题与引用超过 8000 字符，请减少笔记或材料；草稿已保留。')
  return result
}
export function messageContext(content:string):{text:string;context:MessageContext|null}{
  const index=content.lastIndexOf(CONTEXT_MARKER)
  if(index>=0)try{const value=JSON.parse(content.slice(index+CONTEXT_MARKER.length));if(value.version===1&&typeof value.skill==='string'&&Array.isArray(value.sources)&&value.sources.every((s:any)=>typeof s.id==='string'&&typeof s.name==='string'&&typeof s.scope==='string'))return{text:content.slice(0,index),context:value}}catch{}
  const old='\n\n用户明确引用的论文资料（仅有元数据不能作为已读全文）：',at=content.lastIndexOf(old)
  if(at>=0)try{const refs=JSON.parse(content.slice(at+old.length));if(Array.isArray(refs)&&refs.every(p=>typeof p.title==='string'))return{text:content.slice(0,at),context:{version:1,skill:'历史消息未记录',sources:refs.map(p=>({id:p.id||p.title,name:p.title,kind:'paper',scope:'提供的元数据'}))}}}catch{}
  return{text:content,context:null}
}
export function referenceKey(ref:Pick<ComposerReference,'kind'|'id'>){return ref.kind+':'+ref.id}
