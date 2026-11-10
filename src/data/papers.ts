import type { Paper } from '../types'
// Landing-page composition only. These placeholders never enter a research space.
const names=['上传你的论文','整理研究问题','比较技术方法','记录实验依据','追踪阅读笔记','形成科研成果']
const ids=['survey','rag','self-rag','crag','lost-middle','graph-rag']
export const papers:Paper[]=names.map((title,i)=>({id:ids[i]!,title,shortTitle:title,authors:'由你提供论文与原文',year:0,arxiv:'',category:'foundation',tag:'你的研究资料',x:70+(i%3)*330,y:60+Math.floor(i/3)*280,summary:'添加论文后，在自己的研究空间中阅读和分析。',insight:'上传 PDF 或手动添加论文元数据。连接模型后，可精读、比较并保存有明确来源的研究笔记。',questions:[]}))
export const paperById=(id:string)=>papers.find(p=>p.id===id)
export const defaultQuestion='从你的研究问题开始'
export const suggestedQuestions:string[]=[]
