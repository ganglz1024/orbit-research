import type {ReadingRequest} from '../lib/readerContext'
import type {AnalysisPaper,AnalysisTask,ResearchAnalysis,ResearchNote} from '../types/copilot'
/** Explicit backend transport. Existing demo/local data is never silently migrated or overwritten. */
export interface BackendUser { id: string; username: string }
export interface BackendConversation { id: string; title: string; createdAt: string; updatedAt: string; workspaceId?:string|null; deletedAt?:string|null; archivedAt?:string|null; pinnedAt?:string|null; revision?:number; titleManual?:boolean }
export interface BackendMessage { id: string; role: 'user' | 'assistant'; content: string; runId: string; createdAt: string; execution?:{reading?:ReadingRequest;skill?:{primary?:{name:string;id:string;version:string}};model?:{model:string;requestedReasoning?:string}} }
export interface BackendCheckpoint {id:string;summary:string;coveredIds:string[];historyHash:string;status:string;createdAt:string}
export interface BackendRun {
  id: string; conversationId: string; status: 'queued' | 'running' | 'succeeded' | 'failed' | 'partial' | 'cancelled' | 'interrupted'
  answer: string; errorCode: string | null; promptTokens: number | null; completionTokens: number | null
  createdAt: string; updatedAt: string
}
export interface BackendRunEvent { runId: string; eventId: number; type: string; timestamp: string; schemaVersion: number; data: Record<string, unknown> }
export interface BackendDocument { localId?:string; id: string; versionId: string; name: string; hash: string; bytes: number; pageCount: number; parseStatus: 'text_ready' | 'text_truncated' | 'readable_only' | 'parse_failed'; createdAt: string }
export interface BackendSkill { id: string; name: string; version: string; enabled: boolean; status: 'ready'|'provider_required'|'not_implemented'; kind:'builtin'|'custom'; description:string; instructions:string; revision:number; updatedAt:string }
export interface SkillDraft { name:string; description:string; instructions:string; enabled:boolean; revision:number }
export interface ModelConfiguration { baseUrl:string; model:string; hasApiKey:boolean; configured:boolean; timeoutSeconds:number; maxOutputTokens:number; includeUsage:boolean; tokenParameter:'max_tokens'|'max_completion_tokens'; revision:number; source:string; updatedAt:string }
export type ModelDraft = Pick<ModelConfiguration,'baseUrl'|'model'|'timeoutSeconds'|'maxOutputTokens'|'includeUsage'|'tokenParameter'|'revision'> & {apiKey?:string;clearApiKey?:boolean}
export interface ResearchPreference { researchContext:string; defaultSkillId:string; allowSkillTools:boolean; assistantMode:'demo'|'model' }
export class BackendApiError extends Error {
  constructor(public status: number, public code: string, message: string, public requestId: string, public details: unknown) { super(message); this.name = 'BackendApiError' }
}
const prefix = '/api/v1'
let csrf: Promise<{ token: string; headerName: string }> | undefined
async function decode<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new BackendApiError(response.status, payload?.code || 'REQUEST_FAILED', payload?.message || '后端请求未完成。', payload?.requestId || response.headers.get('X-Request-Id') || '', payload)
  return payload as T
}
async function token() {
  csrf ??= fetch(prefix + '/auth/csrf', { credentials: 'same-origin' }).then(decode<{ token: string; headerName: string }>).catch(error => { csrf = undefined; throw error })
  return csrf
}
async function api<T>(path: string, method = 'GET', body?: unknown, options: { signal?: AbortSignal; key?: string } = {}): Promise<T> {
  const headers = new Headers()
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) { const value = await token(); headers.set(value.headerName, value.token) }
  if (options.key) headers.set('Idempotency-Key', options.key)
  const nativeBody = body instanceof FormData || body instanceof URLSearchParams
  if (body !== undefined && !nativeBody) headers.set('Content-Type', 'application/json')
  const response=await fetch(prefix + path, { method, credentials: 'same-origin', headers, signal: options.signal,
    body: body === undefined ? undefined : nativeBody ? body as FormData | URLSearchParams : JSON.stringify(body) })
  if(response.status===401||response.status===403)csrf=undefined
  return decode<T>(response)
}
const segment = (value: string) => encodeURIComponent(value)
export const backendApi = {
  bindReading:(id:string,body:{documentId:string;documentVersion:string;initialPage:number})=>api(`/reading-conversations/${segment(id)}`,'PUT',body),
  readingConversations:(workspaceId:string,documentId:string,documentVersion:string,state='active')=>api<{items:BackendConversation[]}>(`/reading-conversations?workspaceId=${segment(workspaceId)}&documentId=${segment(documentId)}&documentVersion=${segment(documentVersion)}&state=${segment(state)}`),
  createAnalysis:(id:string,body:{task:AnalysisTask;papers:AnalysisPaper[];focus?:string},key:string)=>api<ResearchAnalysis>(`/conversations/${segment(id)}/analyses`,'POST',body,{key}),
  analysis:(id:string)=>api<ResearchAnalysis>(`/analyses/${segment(id)}`),
  analyses:(id:string)=>api<{items:ResearchAnalysis[]}>(`/conversations/${segment(id)}/analyses`),
  cancelAnalysis:(id:string)=>api<ResearchAnalysis>(`/analyses/${segment(id)}/cancel`,'POST'),
  notes:(id?:string)=>api<{items:ResearchNote[]}>(`/notes${id?'?conversationId='+segment(id):''}`),
  saveNote:(id:string,body:{analysisId?:string;title:string;body:string;papers?:AnalysisPaper[]},key:string)=>api<ResearchNote>(`/conversations/${segment(id)}/notes`,'POST',body,{key}),
  editNote:(id:string,body:{title:string;body:string;revision:number})=>api<ResearchNote>(`/notes/${segment(id)}`,'PATCH',body),
  health: () => api<{ status: string; java: string; springBoot: string }>('/health'),
  setupStatus: () => api<{ initialized: boolean }>('/setup'),
  setup: (username: string, password: string) => api('/setup', 'POST', { username, password }),
  async login(username: string, password: string) { await api('/auth/login', 'POST', new URLSearchParams({ username, password })); csrf = undefined; return this.me() },
  async logout() { await api('/auth/logout', 'POST'); csrf = undefined },
  me: () => api<BackendUser>('/auth/me'),
  model:()=>api<ModelConfiguration>('/settings/model'),
  saveModel:(body:ModelDraft)=>api<ModelConfiguration>('/settings/model','PUT',body),
  testModel:()=>api<{connected:boolean;model:string}>('/settings/model/test','POST'),
  preferences:()=>api<ResearchPreference>('/settings/preferences'),
  savePreferences:(body:ResearchPreference)=>api<ResearchPreference>('/settings/preferences','PUT',body),
  skills:()=>api<{items:BackendSkill[]}>('/skills'),
  createSkill:(body:SkillDraft)=>api<BackendSkill>('/skills','POST',body),
  importGithubSkill:(url:string)=>api<{skill:BackendSkill;sourceUrl:string;commitSha:string;contentHash:string;reused:boolean;mode:string}>('/skills/import/github','POST',{url}),
  updateSkill:(id:string,body:SkillDraft)=>api<BackendSkill>(`/skills/${segment(id)}`,'PUT',body),
  toggleSkill:(id:string,enabled:boolean)=>api<BackendSkill>(`/skills/${segment(id)}/enabled`,'PATCH',{enabled}),
  deleteSkill:(id:string)=>api<void>(`/skills/${segment(id)}`,'DELETE'),
  conversations: (page = 0, size = 20, filters:{q?:string;workspaceId?:string;state?:string}={}) => api<{ items: BackendConversation[]; total: number; page: number; size: number }>(`/conversations?page=${page}&size=${size}&q=${segment(filters.q||'')}&state=${segment(filters.state||'active')}${filters.workspaceId?'&workspaceId='+segment(filters.workspaceId):''}`),
  createConversation: (title: string, workspaceId?:string) => api<BackendConversation>('/conversations', 'POST', { title, workspaceId }),
  conversation: (id: string) => api<{ conversation: BackendConversation; messages: BackendMessage[] }>(`/conversations/${segment(id)}`),
  compactPreview:(id:string)=>api<BackendCheckpoint>(`/conversations/${segment(id)}/compact/preview`,'POST'),
  compactApply:(id:string,checkpoint:string)=>api<BackendCheckpoint>(`/conversations/${segment(id)}/compact/${segment(checkpoint)}/apply`,'POST'),
  compactStatus:(id:string)=>api<{checkpoint:BackendCheckpoint|''}>(`/conversations/${segment(id)}/compact`),
  renameConversation: (id: string, title: string, revision?:number, automatic=false) => api<BackendConversation>(`/conversations/${segment(id)}`, 'PATCH', { title,revision,automatic }),
  manageConversation:(id:string,action:string,revision?:number)=>api<BackendConversation>(`/conversations/${segment(id)}/manage`,'POST',{action,revision}),
  associateConversation:(id:string,workspaceId:string)=>api<BackendConversation>(`/conversations/${segment(id)}/workspace`,'PATCH',{workspaceId}),
  createRun: (conversationId: string, input: { prompt: string; documentIds?: string[]; skillId?: string; toolSkillIds?:string[]; installSkillUrl?:string;reasoningEffort?:string;responseStyle?:string;readingContext?:ReadingRequest }, idempotencyKey: string) => api<{ runId: string; run: BackendRun }>(`/conversations/${segment(conversationId)}/runs`, 'POST', input, { key: idempotencyKey }),
  run: (id: string) => api<BackendRun>(`/runs/${segment(id)}`),
  cancelRun: (id: string) => api<BackendRun>(`/runs/${segment(id)}/cancel`, 'POST'),
  associateDocument:(conversationId:string,documentId:string)=>api<void>(`/conversations/${segment(conversationId)}/documents/${segment(documentId)}`,'POST'),
  documents: (conversationId: string) => api<{ items: BackendDocument[] }>(`/conversations/${segment(conversationId)}/documents`),
  uploadDocuments(conversationId: string, files: File[], signal?: AbortSignal) {
    const form = new FormData(); form.set('conversationId', conversationId); files.forEach(file => form.append('files', file))
    return api<{ items: Array<{ filename: string; document: BackendDocument | null; reused: boolean; code: string | null; message: string | null }> }>('/documents', 'POST', form, { signal })
  },
  contentUrl: (id: string, versionId: string) => `${prefix}/documents/${segment(id)}/versions/${segment(versionId)}/content`,
  subscribeRun(id: string, onEvent: (event: BackendRunEvent) => void, onReconnect?: () => void, after = 0) {
    const stream = new EventSource(`${prefix}/runs/${segment(id)}/events?after=${after}`)
    const terminal = new Set(['run_completed', 'run_failed', 'run_cancelled'])
    for (const name of ['run_queued', 'run_started', 'step_started', 'skill_called', 'answer_delta', 'warning', 'skill_installed', ...terminal]) {
      stream.addEventListener(name, event => {
        const value = JSON.parse((event as MessageEvent<string>).data) as BackendRunEvent
        if (terminal.has(name)) stream.close()
        onEvent(value)
      })
    }
    stream.onerror = () => onReconnect?.() // Native EventSource reconnects with Last-Event-ID.
    return () => stream.close()
  },
}
