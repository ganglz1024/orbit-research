export function compactAuthors(value?:string){
 const authors=(value||'').split(/[,，;；、]/).map(s=>s.trim()).filter(Boolean)
 if(!authors.length)return '作者未提供'
 if(authors.length===1)return authors[0]!
 const first=authors[0]!,name=/[\u4e00-\u9fff]/.test(first)?first:first.split(/\s+/).at(-1)!
 return name+' et al.'
}
export function floatingPosition(anchor:{left:number;right:number;top:number;bottom:number},width:number,height:number,clip:{left:number;right:number;top:number;bottom:number}){
 const left=Math.max(clip.left+8,Math.min((anchor.left+anchor.right-width)/2,clip.right-width-8))
 const below=anchor.bottom+8,top=below+height<=clip.bottom-8?below:anchor.top-height-8
 return {left,top:Math.max(clip.top+8,Math.min(top,clip.bottom-height-8))}
}
export type ReaderLayout='full'|'ai'|'focus'
export function readerLayoutState(mode:ReaderLayout){return {navigation:mode==='full',inspector:mode!=='focus'}}
