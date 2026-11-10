import type { MetadataEvidence,Paper,PaperRelation } from '../types'

export interface MetadataPage { page:number; text:string }
const comparable=(text:string)=>text.normalize('NFKC').toLocaleLowerCase().replace(/[\p{P}\p{S}\s]+/gu,'')
export function paperDisplayTitle(paper:Pick<Paper,'title'|'localDocumentId'>){
  return paper.localDocumentId?paper.title.replace(/\.pdf$/i,'').replace(/^\d{1,3}[_\s-]+/,'').replace(/_/g,' ').trim():paper.title
}
export function parseTaskJson(answer:string):unknown {
  try{return JSON.parse(answer.trim().replace(/^```(?:json)?\s*|\s*```$/g,''))}catch{throw new Error('模型未返回有效 JSON，本次没有覆盖已有资料。')}
}
/** A model-proposed field is accepted only when its quote and value occur in a provided PDF page. */
export function verifyPaperMetadata(answer:string,pages:MetadataPage[]){
  const raw=parseTaskJson(answer) as Record<string,unknown>
  if(!raw||typeof raw!=='object'||!Array.isArray(raw.evidence))throw new Error('模型未提供可核对的题录依据。')
  const evidence:MetadataEvidence[]=raw.evidence.filter((e:any)=>e&&['title','authors','year'].includes(e.field)&&Number.isInteger(e.page)&&typeof e.quote==='string'&&comparable(e.quote).length>=4&&e.quote.length<=2200&&pages.some(p=>p.page===e.page&&comparable(p.text).includes(comparable(e.quote))))
  const supported=(field:MetadataEvidence['field'],value:string)=>comparable(value).length>=2&&evidence.some(e=>e.field===field&&comparable(e.quote).includes(comparable(value)))
  const title=typeof raw.title==='string'&&raw.title.trim().length>=4&&raw.title.length<=500&&supported('title',raw.title)?raw.title.trim():undefined
  const authorNames=Array.isArray(raw.authors)?raw.authors.filter((name:unknown):name is string=>typeof name==='string'&&name.trim().length>=2&&name.length<=100):[]
  const authors=authorNames.length>0&&authorNames.length<=60&&authorNames.every(name=>supported('authors',name))?[...new Set(authorNames.map(n=>n.trim()))].join(', '):undefined
  const yearContext=evidence.some(e=>e.field==='year'&&/arxiv|preprint|submitted|published|conference|neurips|nips|iclr|\bacl\b|emnlp|copyright|发表|出版|会议|\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\b/i.test(e.quote)&&comparable(e.quote).includes(String(raw.year)))
  const year=typeof raw.year==='number'&&Number.isInteger(raw.year)&&raw.year>=1900&&raw.year<=new Date().getFullYear()+1&&yearContext?raw.year:undefined
  const summary=typeof raw.summary==='string'?raw.summary.trim().slice(0,900):''
  const yearQuotes=evidence.filter(e=>e.field==='year'&&year&&comparable(e.quote).includes(String(year))).map(e=>e.quote).join(' ')
  const yearBasis:Paper['yearBasis']=!year?'unknown':/published|conference|neurips|nips|iclr|\bacl\b|emnlp|发表|出版|会议/i.test(yearQuotes)?'publication':/arxiv|preprint|submitted/i.test(yearQuotes)?'preprint':'unknown'
  const missing=[!title?'题名':'',!authors?'作者':'',!year?'年份':''].filter(Boolean)
  if(!title&&!authors&&!year)throw new Error('模型题录没有通过原文核对，保留已有资料。可尝试手动补充或更换模型。')
  return {title,authors,year,summary,yearBasis,evidence,missing}
}
export function verifyPaperRelations(answer:string,papers:Paper[],runId:string):PaperRelation[]{
  const raw=parseTaskJson(answer) as {edges?:unknown}
  if(!raw||!Array.isArray(raw.edges)||raw.edges.length>30)throw new Error('模型未返回有效的关联结构，已有连线已保留。')
  const allowed=new Set(papers.map(p=>p.id)),seen=new Set<string>(),result:PaperRelation[]=[]
  for(const edge of raw.edges){
    if(!edge||!allowed.has(edge.from)||!allowed.has(edge.to)||edge.from===edge.to||typeof edge.reason!=='string'||edge.reason.trim().length<4||edge.reason.length>800)throw new Error('模型关联包含未知论文或缺少依据，已有连线已保留。')
    const key=[edge.from,edge.to].sort().join('|');if(seen.has(key))continue;seen.add(key)
    result.push({from:edge.from,to:edge.to,reason:edge.reason.trim(),kind:['topic','method','background'].includes(edge.kind)?edge.kind:'topic',runId,createdAt:new Date().toISOString()})
  }
  return result
}
