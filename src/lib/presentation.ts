import type { Deck } from '../types'
export async function exportPresentation(deck:Deck) {
  const PptxGenJS=(await import('pptxgenjs')).default
  const pptx=new PptxGenJS();pptx.layout='LAYOUT_WIDE';pptx.author='Orbit 研迹';pptx.subject=deck.runId?'AI 辅助阅读汇报；请核对原文':'阅读汇报草稿；请核对原文';pptx.title=deck.title
  deck.slides.forEach((content,index)=>{
    const slide=pptx.addSlide(),cover=content.type==='cover'
    slide.background={color:cover?'EDE6F4':'FAF9F6'}
    slide.addText('ORBIT / RESEARCH NOTES',{x:.65,y:.38,w:7,h:.3,fontFace:'Microsoft YaHei',fontSize:10,color:'7356A6',charSpacing:3})
    if(cover){slide.addShape(pptx.ShapeType.ellipse,{x:8.8,y:1.1,w:5,h:5,line:{color:'D0C1E2',width:1},fill:{color:'EDE6F4',transparency:100}});slide.addShape(pptx.ShapeType.ellipse,{x:9.5,y:1.8,w:3.5,h:3.5,line:{color:'B9A2D0',width:1},fill:{color:'EDE6F4',transparency:100}})}
    slide.addText(content.title,{x:.72,y:cover?2:1.05,w:cover?10.8:11.8,h:cover?2:1.05,fontFace:'Microsoft YaHei',fontSize:cover?35:27,bold:true,color:'322E3B',breakLine:false,margin:0,fit:'shrink'})
    slide.addText(content.body,{x:.75,y:cover?4.45:2.35,w:11.7,h:cover?1:4.1,fontFace:'Microsoft YaHei',fontSize:content.type==='references'?11:cover?16:20,color:'625A6D',paraSpaceAfter:12,margin:0,fit:'shrink',breakLine:false})
    slide.addText('阅读草稿 · 请核对原文',{x:.75,y:7.02,w:10.5,h:.2,fontFace:'Microsoft YaHei',fontSize:8,color:'6D6477'})
    slide.addText(String(index+1).padStart(2,'0'),{x:11.8,y:6.97,w:.7,h:.3,fontFace:'Microsoft YaHei',fontSize:11,color:'7356A6',align:'right'})
    slide.addNotes('本页是可编辑的科研阅读草稿。'+(deck.runId?'AI 任务：'+deck.runId+'。':'')+'请依据提供的原文核验论述；主题联系不能作为引用关系证据。')
  })
  await pptx.writeFile({fileName:`Orbit-${deck.title.replace(/[<>:"/\\|?*]/g,'').slice(0,45)}-v${deck.version}.pptx`})
}


