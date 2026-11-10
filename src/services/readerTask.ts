import {contextPrompt} from '../lib/composerContext'
import {runResearchTask} from './researchTasks'
import {readingPrompt,type ReadingAnchor} from '../lib/reading'
/** Excerpt authorization deliberately supplies no document IDs: never upload the containing PDF. */
export function runReaderSelection(anchor:ReadingAnchor,mode:'overview'|'study'|'critical',operation:'explain'|'translate'|'formula',signal?:AbortSignal){
 const prompt=contextPrompt(readingPrompt(anchor,mode,operation),{version:1,skill:'chat',skillName:'PDF 选区解释',sources:[{id:anchor.documentId,kind:'excerpt',name:'PDF 第 '+(anchor.pageIndex+1)+' 页的明确选区',versionId:anchor.documentVersion,scope:'仅授权浏览器选区；未提供全文；尚未经后端原文重新核验。PDF 页序 '+(anchor.pageIndex+1)}]})
 return runResearchTask(prompt,{title:operation==='translate'?'论文片段翻译':operation==='formula'?'论文公式解释':'论文片段精读',signal})
}
