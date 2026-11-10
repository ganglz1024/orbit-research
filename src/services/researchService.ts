import type { Paper } from '../types'
export interface ResearchService { discover(question:string,signal:AbortSignal,reducedMotion:boolean):AsyncGenerator<Paper> }
// No fabricated search results: this transport is reserved for a real academic provider.
export const researchService:ResearchService={async *discover(){throw new Error('学术检索接口尚未配置，请先上传或手动添加已有论文。')}}
