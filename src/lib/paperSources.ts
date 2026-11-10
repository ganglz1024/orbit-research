import type { Paper } from '../types'
export function paperUrl(paper:Paper,workspaceId='default'):string {
  if(paper.localDocumentId)return readerUrl(paper.localDocumentId,workspaceId)
  if(paper.url&&/^https?:\/\//i.test(paper.url))return paper.url
  return paper.arxiv?'https://arxiv.org/abs/'+encodeURIComponent(paper.arxiv):''
}
export function paperEvidence(paper:Paper){return {id:paper.id,title:paper.title,authors:paper.authors,year:paper.year||undefined,url:paper.url||(paper.arxiv?'https://arxiv.org/abs/'+paper.arxiv:undefined),metadata:paper.summary||undefined,documentId:paper.backendDocumentId}}

export function readerUrl(documentId:string,workspaceId='default'){return '/documents/'+encodeURIComponent(documentId)+'/read?workspace='+encodeURIComponent(workspaceId||'default')}
