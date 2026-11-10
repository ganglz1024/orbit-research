import type {ReadingRequest} from './lib/readerContext'
export interface Paper {
  id: string; title: string; shortTitle: string; authors: string; year: number; arxiv: string
  category: 'foundation' | 'retrieval' | 'reliability'; tag: string; summary: string; insight: string
  questions: string[]; x: number; y: number
  url?: string; localDocumentId?: string; backendDocumentId?: string; source?: 'manual' | 'pdf'
  metadataState?: 'ready' | 'insufficient_data' | 'error'
  metadataError?: string; metadataRunId?: string; metadataEvidence?: MetadataEvidence[]
  yearBasis?: 'publication' | 'preprint' | 'unknown'
}
export interface MetadataEvidence { field:'title'|'authors'|'year'; page:number; quote:string }
export interface PaperRelation { from:string; to:string; reason:string; kind:'topic'|'method'|'background'; runId:string; createdAt:string }
export interface Message { id: string; role: 'user' | 'assistant'; content: string; paperIds?: string[] }
export interface Session { id: string; title: string; customTitle?: string; query: string; paperIds: string[]; selectedIds: string[]; messages: Message[]; edges?:[string,string][]; relationDetails?:PaperRelation[]; createdAt: string }
export interface Slide { id: string; title: string; body: string; type: 'cover' | 'content' | 'references'; sources: string[] }
export interface Deck { id: string; title: string; slides: Slide[]; createdAt: string; version: number; runId?: string }
export interface Annotation {
  id: string; page: number; type: 'highlight' | 'underline' | 'note'; quote: string; comment: string
  rects: number[][]; color: string; createdAt: string; origin: 'user' | 'ai'; runId?:string; documentVersion?:string;conversationId?:string;messageId?:string;question?:string;workspaceId?:string;ownerId?:string;paperId?:string;noteType?:'excerpt'|'understanding'|'ai'|'question';readingSource?:ReadingRequest
}
export interface LocalDocument {
  id: string; name: string; bytes: ArrayBuffer; hash: string; pageCount: number; createdAt: string
  annotations: Annotation[]; lastPage: number; demo?: boolean; bookmarks?:number[]; readingState?:{scale:number;rotation:number;openedAt:string}
}
export interface DocumentMeta { id: string; name: string; pageCount: number; createdAt: string; demo?: boolean }
