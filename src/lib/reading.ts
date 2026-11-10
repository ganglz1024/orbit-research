export interface ReadingAnchor {documentId:string;documentVersion:string;pageIndex:number;quote:string;rects:number[][];coordinateSystem:'pdf-points';scope:'selection';parserVersion:string}
export function selectionAnchor(documentId:string,hash:string,page:number,quote:string,rects:number[][]):ReadingAnchor{
 if(!documentId||!hash||!Number.isInteger(page)||page<1||!quote.trim()||quote.length>4000)throw new Error('请选择同一页内不超过 4000 字的原文。')
 if(rects.some(r=>r.length!==8||r.some(x=>!Number.isFinite(x))))throw new Error('选区位置无效，请重新选择。')
 return {documentId,documentVersion:hash,pageIndex:page-1,quote,rects:rects.map(r=>[...r]),coordinateSystem:'pdf-points',scope:'selection',parserVersion:'pdfjs-selection-v1'}
}
export function findPageMatches(pages:{page:number;text:string}[],query:string){const q=query.trim().toLocaleLowerCase();if(!q)return [];return pages.flatMap(p=>{const at=p.text.toLocaleLowerCase().indexOf(q);return at<0?[]:[{page:p.page,snippet:p.text.slice(Math.max(0,at-45),at+q.length+100)}]})}
export function readingPrompt(anchor:ReadingAnchor,mode:'overview'|'study'|'critical',operation:'explain'|'translate'|'formula'){
 const strategies={overview:'简洁概述这段内容的研究问题、方法和贡献。',study:'先给直观解释，再补前置知识、符号与逻辑，必要时给明确标为自构教学示例的例子，最后给一个可选理解问题。',critical:'区分片段明确支持的事实、证据不足的结论与你的推断；分析该片段能够支持和不能支持什么。'}
 return (operation==='translate'?'准确翻译所选原文并保留术语。':operation==='formula'?'解释选区中的公式及符号；无法从选区辨识的公式或编号请说明，不自行补造。':'解释用户明确选中的 PDF 原文。')+strategies[mode]+'本轮仅授权以下选区，不代表拥有整篇论文；不得声称已经读取全文、其他页或原文之外的表格。来源为浏览器PDF文本选区，尚未经后端原文重新核验。只可引用所给PDF页序。\n'+JSON.stringify(anchor)
}
