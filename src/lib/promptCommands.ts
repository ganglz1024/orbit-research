export interface PromptEntry {id:string;name:string;kind:'skill'|'paper'|'document'|'note'|'command';description?:string;aliases?:string[];category?:string;available?:boolean}
export interface PromptToken {kind:'slash'|'mention';query:string;start:number;end:number}
export function fuzzyCommandMatch(query:string,text:string){const q=query.toLowerCase(),s=text.toLowerCase();if(!q||s.includes(q))return true;let i=0;for(const char of s)if(char===q[i])i++;return i===q.length}
export function promptToken(text:string,caret:number):PromptToken|null {
  const part=text.slice(0,caret),match=/(?:^|\s)([/@])([^\s\[\]]*)$/.exec(part)
  if(!match)return null
  return {kind:match[1]==='/'?'slash':'mention',query:match[2]||'',start:caret-(match[2]?.length||0)-1,end:caret}
}
export function insertPromptEntry(text:string,token:PromptToken,entry:PromptEntry){
  const prefix=token.kind==='slash'?'/':'@',replacement=prefix+'['+(/[\[\]]/.test(entry.name)?entry.id:entry.name)+'] '
  return {text:text.slice(0,token.start)+replacement+text.slice(token.end),caret:token.start+replacement.length}
}
function findEntry(name:string,entries:PromptEntry[],kind?:'skill'){
  const found=entries.filter(e=>(!kind||e.kind===kind)&&(e.id===name||e.name===name||e.aliases?.includes(name)))
  if(found.length!==1)throw new Error(found.length?'名称重复，请使用列表中的唯一标识：'+name:'没有找到可用的 Skill 或材料：'+name)
  return found[0]!
}
export function parsePromptCommands(text:string,entries:PromptEntry[]){
  let prompt=text.trim();let skill:PromptEntry|undefined
  prompt=prompt.replace(/(^|\s)\/(?:\[([^\]]+)\]|([^\s]+))/g,(_whole,before,quoted,plain)=>{const selected=findEntry(quoted||plain,entries,'skill');if(skill&&skill.id!==selected.id)throw new Error('一次消息请显式指定一个 Skill。');skill=selected;return before}).trim()
  const mentions:PromptEntry[]=[]
  prompt=prompt.replace(/(^|\s)@(?:\[([^\]]+)\]|([^\s]+))/g,(_whole,before,quoted,plain)=>{
    const entry=findEntry(quoted||plain,entries);if(!mentions.some(e=>e.id===entry.id&&e.kind===entry.kind))mentions.push(entry)
    if(entry.kind==='skill'){if(skill&&skill.id!==entry.id)throw new Error('一次消息请显式指定一个 Skill。');skill=entry}
    return before+(entry.kind==='skill'?'':'【'+entry.name+'】')
  }).trim()
  return {prompt:prompt||'请依据本次明确提供的材料执行所选 Skill。',skill,mentions}
}
