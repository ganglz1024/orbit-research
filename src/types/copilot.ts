export type AnalysisTask='plain'|'method'|'contribution'|'experiment'|'limitations'|'formula'|'compare'|'common'|'methods'|'conclusions'|'relation'
export type AnalysisState='idle'|'loading'|'success'|'error'|'insufficient_data'
export interface AnalysisPaper {id:string;title:string;authors?:string;year?:number;url?:string;metadata?:string;documentId?:string}
export interface AnalysisSource extends AnalysisPaper {versionId:string;hash:string;scope:'pdf_excerpt'|'provided_metadata';excerpts:Array<{id:string;page:number|null;text:string}>}
export interface Evidence {sourceId:string;excerptId:string;page:number|null;quote:string;kind:'original_support'|'provided_metadata'|'unverified';explanation:string}
export interface AnalysisResult {title:string;summary:string;details:string;evidence:Evidence[];comparison:Array<{dimension:string;cells:Array<{paperId:string;text:string;evidence:Evidence[]}>}>;commonalities:string[];differences:string[];directions:string[];suggestions:string[]}
export interface ResearchAnalysis {id:string;conversationId:string;task:AnalysisTask;state:AnalysisState;error:string;runId:string|null;sources:AnalysisSource[];result:AnalysisResult|null;createdAt:string}
export interface ResearchNote {id:string;conversationId:string;analysisId:string|null;title:string;body:string;snapshot:ResearchAnalysis|{kind:'user_note';sources:AnalysisSource[]};revision:number;createdAt:string;updatedAt:string}
export const taskLabels:Record<AnalysisTask,string>={plain:'通俗解释',method:'研究方法',contribution:'创新点分析',experiment:'实验结果',limitations:'研究局限',formula:'解释公式',compare:'AI 对比分析',common:'共同研究方向',methods:'方法差异',conclusions:'研究结论对比',relation:'解释主题联系'}
