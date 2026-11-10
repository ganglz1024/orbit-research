import MarkdownIt from 'markdown-it'
import katex from 'katex'
const md=new MarkdownIt({html:false,linkify:true,breaks:true})
// Keep model-provided images from making third-party requests.
md.renderer.rules.image=(tokens,index)=>md.utils.escapeHtml(tokens[index]?.content||'[图片]')
md.renderer.rules.link_open=(tokens,index,options,_env,renderer)=>{tokens[index]!.attrSet('target','_blank');tokens[index]!.attrSet('rel','noopener noreferrer');return renderer.renderToken(tokens,index,options)}
md.inline.ruler.before('escape','research_math',(state,silent)=>{
  const start=state.pos,source=state.src
  const open=source.startsWith('\\(',start)?'\\(':source.startsWith('\\[',start)?'\\[':source[start]==='$'?(source[start+1]==='$'?'$$':'$'):''
  if(!open)return false
  const close=open==='\\('? '\\)':open==='\\['?'\\]':open,end=source.indexOf(close,start+open.length)
  if(end<0||end-start>6000)return false
  if(!silent){const token=state.push('research_math','',0);token.content=source.slice(start+open.length,end);token.meta={display:open==='$$'||open==='\\['}}
  state.pos=end+close.length;return true
})
md.renderer.rules.research_math=(tokens,index)=>katex.renderToString(tokens[index]!.content,{displayMode:!!tokens[index]!.meta?.display,throwOnError:false,trust:false,strict:'warn',maxExpand:500,maxSize:20,output:'htmlAndMathml'})
export const renderResearchMarkdown=(text:string)=>md.render(text)
