import type {ResearchNote,ResearchAnalysis} from '../types/copilot'
export function researchNoteMarkdown(note:ResearchNote){
  const snapshot=note.snapshot as ResearchAnalysis
  const escape=(s:string)=>s.replaceAll('|','\\|').replaceAll('\n',' ')
  let body=`# ${note.title}\n\n${note.body}\n\n`
  if(snapshot.result?.comparison.length){body+='## 保存时的论文对比\n\n|维度|'+snapshot.sources.map(s=>escape(s.title)).join('|')+'|\n|---|'+snapshot.sources.map(()=>'---').join('|')+'|\n';for(const row of snapshot.result.comparison)body+='|'+row.dimension+'|'+row.cells.map(c=>escape(c.text)).join('|')+'|\n'}
  if(snapshot.result)for(const g of [{title:'共同研究方向 - AI 归纳',items:snapshot.result.commonalities},{title:'方法差异 - AI 归纳',items:snapshot.result.differences},{title:'后续方向 - AI 推测，待验证',items:snapshot.result.directions}])if(g.items.length)body+='\n## '+g.title+'\n\n'+g.items.map(s=>'- '+s).join('\n')+'\n'
  body+='\n## 来源与范围\n\n'+note.snapshot.sources.map(s=>`- ${s.title}：${s.scope==='pdf_excerpt'?'所选 PDF 片段，非全文覆盖声明':'提供的元数据，待全文核验'}${s.url?' '+s.url:''}`).join('\n')
  if(snapshot.result){const evidence=[...snapshot.result.evidence,...snapshot.result.comparison.flatMap(r=>r.cells.flatMap(c=>c.evidence))];const seen=new Set<string>();for(const e of evidence){const key=JSON.stringify([e.sourceId,e.excerptId,e.quote]);if(seen.has(key))continue;seen.add(key);body+=`\n\n> ${e.quote}\n\n来源 ${e.sourceId}${e.page?'，PDF 第 '+e.page+' 页':''}；${e.kind}。${e.explanation}`}}
  return body+`\n\n保存时间：${note.createdAt}；笔记修订 ${note.revision}。\n`
}
