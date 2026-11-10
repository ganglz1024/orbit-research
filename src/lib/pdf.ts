import { getDocument as loadPdf, GlobalWorkerOptions, TextLayer } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { getDocument, saveDocument } from './storage'
import type { LocalDocument } from '../types'
GlobalWorkerOptions.workerSrc=workerUrl
export { loadPdf, TextLayer }

export async function importPdf(file:File):Promise<LocalDocument> {
  if(file.size>50*1024*1024) throw new Error('单份 PDF 不能超过 50 MB。')
  if(!file.name.toLowerCase().endsWith('.pdf')) throw new Error('请选择 PDF 格式的文献。')
  const bytes=await file.arrayBuffer()
  const header=new TextDecoder().decode(bytes.slice(0,1024))
  if(!header.includes('%PDF-')) throw new Error('文件内容不是有效 PDF，请检查格式。')
  const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))).map(x=>x.toString(16).padStart(2,'0')).join('')
  const id='pdf-'+hash.slice(0,24),existing=await getDocument(id)
  if(existing)return existing
  const task=loadPdf({data:new Uint8Array(bytes.slice(0)),isEvalSupported:false})
  try {
    const pdf=await task.promise
    const result:LocalDocument={id,name:file.name,bytes,hash,pageCount:pdf.numPages,createdAt:new Date().toISOString(),annotations:[],lastPage:1}
    await saveDocument(result);await pdf.destroy();return result
  } catch(error) {
    await task.destroy()
    if((error as Error).name==='PasswordException')throw new Error('PDF 受密码保护，请解除保护后重新上传。')
    throw new Error('无法打开此 PDF，请检查文件是否完整，或浏览器是否允许本地存储。')
  }
}
export async function exportAnnotated(doc:LocalDocument) {
  const {PDFDocument,PDFName,PDFHexString,PDFArray}=await import('pdf-lib')
  const pdf=await PDFDocument.load(doc.bytes.slice(0))
  for(const note of doc.annotations){
    const page=pdf.getPage(note.page-1);if(!page||!note.rects.length)continue
    const points=note.rects.flat(),xs=points.filter((_,i)=>i%2===0),ys=points.filter((_,i)=>i%2===1)
    const color=[1,3,5].map(i=>parseInt(note.color.slice(i,i+2),16)/255)
    const dictionary:Record<string,unknown>={Type:'Annot',Subtype:note.type==='highlight'?'Highlight':note.type==='underline'?'Underline':note.quote==='区域批注'?'Square':'Text',Rect:[Math.min(...xs),Math.min(...ys),Math.max(...xs),Math.max(...ys)],C:color,Contents:PDFHexString.fromText((note.origin==='ai'?'阅读提示\n':'')+(note.comment||note.quote)),T:PDFHexString.fromText('Orbit 研迹'),NM:PDFHexString.fromText(note.id),F:4}
    if(note.type!=='note'){dictionary.QuadPoints=points;dictionary.CA=.4}
    const ref=pdf.context.register(pdf.context.obj(dictionary as any))
    let annots=page.node.lookupMaybe(PDFName.of('Annots'),PDFArray)
    if(!annots){annots=pdf.context.obj([]);page.node.set(PDFName.of('Annots'),annots)}annots.push(ref)
  }
  return new Uint8Array(await pdf.save()).buffer as ArrayBuffer
}
