import type {ComposerReference} from './composerContext'
export interface EvidenceBlock {chunkId:string;pageIndex:number;quote:string;scope:'selection'|'nearby'|'authorized-block';rects:number[][]}
export interface ReadingRequest {documentId:string;documentVersion:string;evidence:EvidenceBlock[]}
export interface ReaderComposerBridge {
 key:string;mentions:ComposerReference[];referenceSelection:()=>void;
 citation:{title:string;source:ReadingRequest};removeCitation:()=>void;locateCitation:(source:ReadingRequest)=>void;restoreCitation:(source:ReadingRequest)=>void;
 send:(input:{text:string;skillId:string;references:ComposerReference[];reasoningEffort?:string;responseStyle:string;readingContext:ReadingRequest})=>Promise<boolean>;
 fresh:()=>Promise<void>
}
// DOM ranges may repeat a rectangle for nested font spans. Keep actual quads only.
export function uniqueSelectionRects(rects:number[][]):number[][]{
 const seen=new Set<string>();return rects.filter(r=>{if(r.length!==8||r.some(v=>!Number.isFinite(v)))return false;const key=r.join(',');if(seen.has(key))return false;seen.add(key);return true})
}
export function textBlocks(pageIndex:number,text:string):EvidenceBlock[]{
 // Page text is split into bounded sentence groups, not claimed as verified semantic paragraphs.
 const units=text.match(/[^。！？.!?]+[。！？.!?]*\s*/g)||[text];let quote='',blocks:EvidenceBlock[]=[];
 function push(){if(quote.trim())blocks.push({chunkId:'p'+pageIndex+'-b'+blocks.length,pageIndex,quote:quote.trim(),scope:'authorized-block',rects:[]});quote=''}
 for(const unit of units){if(quote.length+unit.length>900)push();for(let i=0;i<unit.length;i+=900){if(i)push();quote+=unit.slice(i,i+900)}}push();return blocks
}
const compact=(s:string)=>s.replace(/\s+/g,'').toLowerCase()
export function nearbyPreview(blocks:EvidenceBlock[],pageIndex:number,selected:string):EvidenceBlock|null{
 const q=compact(selected);if(q.length<4)return null
 const page=blocks.filter(b=>b.pageIndex===pageIndex),all=page.map(b=>compact(b.quote)).join(''),at=all.indexOf(q);if(at<0||all.indexOf(q,at+q.length)>=0)return null
 let offset=0,start=-1,end=-1;for(let i=0;i<page.length;i++){const length=compact(page[i]!.quote).length;if(at>=offset&&at<offset+length)start=i;if(at+q.length>offset&&at+q.length<=offset+length)end=i;offset+=length}
 if(start<0||end<0)return null
 const chosen=page.slice(Math.max(0,start-1),Math.min(page.length,end+2)),quote=chosen.map(b=>b.quote).join('\n');if(quote.length>4000)return null
 return{chunkId:chosen.map(b=>b.chunkId).join('+'),pageIndex,quote,scope:'nearby',rects:[]}
}
export function incompleteSelection(text:string){return !/[.!?。！？;；:)）\]”"']$/.test(text.trim())||/\b(as|we|do|not|and|because|the|of|to|with|where|which)\s*$/i.test(text)}
export function retrieveEvidence(allowed:EvidenceBlock[],question:string,budget=4000):EvidenceBlock[]{
 const tokens=[...new Set(question.toLowerCase().match(/[a-z][a-z0-9_-]+|[\u4e00-\u9fff]{1,2}/g)||[])];
 const ranked=allowed.map((b,i)=>({b,i,score:tokens.reduce((n,t)=>n+(b.quote.toLowerCase().includes(t)?1:0),0)})).sort((a,b)=>b.score-a.score||a.i-b.i);
 let used=0;const result:EvidenceBlock[]=[];for(const {b}of ranked){if(result.length>=4||used+b.quote.length>budget)continue;if(!result.some(r=>r.quote===b.quote)){result.push({...b,rects:b.rects.map(r=>[...r])});used+=b.quote.length}}return result
}
export function validateReadingScope(input:ReadingRequest,docId:string,version:string){if(input.documentId!==docId||input.documentVersion!==version)throw new Error('来源版本不匹配。');if(input.evidence.length>6||input.evidence.reduce((n,e)=>n+e.quote.length,0)>6000)throw new Error('证据超过预算。');return input}
export function readingInstruction(mode:'overview'|'study'|'critical',question:string){
 const detailed=/详细|逐步|深入|完整|推导|背景.*数学|in.detail|step.by.step/i.test(question)
 const strategy={overview:'先简洁回答核心问题，再给少量要点。',study:detailed?'本轮明确要求深入：充分解释研究背景、数学原理和论证逻辑，按需要逐步推导，不因默认简洁而省略关键内容。':'普通段落默认先用一小段中文直接说明含义，再补充2至3个必要要点；不套用整篇论文分析或审稿模板。需要时才解释前置知识。',critical:'区分作者主张、提供片段、支持程度与你的推断。'}[mode];
 return '阅读策略：'+strategy+'用户本次明确的任务、语言、格式和详细程度优先。思考强度不决定回答长度。来源范围末尾简要说明一次，完整执行信息留在查看来源，不重复免责声明。'+'公式任务优先解释符号、矩阵维度、设计动机和必要推导，自行构造的数值示例必须标为教学示例并检查维度；表格任务核对行列、指标方向、数值及结论边界，缺损表格不得补造。当前接口只提供文本，未发送任何区域图像，不得声称看到了原图。PDF文本层可能损坏上下标或表格顺序，无法辨识时明确说明。只把本轮结构化证据中真实提供的片段当来源；未提供全文时不声称读过全文，无片段时可用模型知识但需说明。如果用户要求只依据本 PDF，而现有片段不足，应说明证据不足，不能用模型记忆冒充原文。后续追问可沿用本阅读对话已明确授权的片段；需要扩大范围时请建议用户补充，不自行猜写原文。'
}
