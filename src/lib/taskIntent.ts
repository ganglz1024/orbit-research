/** Conservative routing: explicit selections always win; questions about tools are not execution requests. */
export function taskIntent(text:string,explicit=''){
  if(explicit)return explicit
  if(/(制作|生成|做|创建|整理).{0,24}(ppt|幻灯片|汇报)|(?:ppt|幻灯片|汇报).{0,16}(制作|生成)/i.test(text)&&!/(怎么|如何|能否|能不能).{0,10}(调用|实现|开发)/.test(text))return 'ppt_generate'
  if(/(规划|制定).{0,24}(研究|学习|阅读).{0,10}(路线|计划)/.test(text))return 'research_plan'
  if(/(比较|对比).{0,40}(论文|文章|Attention|LoRA)|(?:论文|文章).{0,20}(比较|对比)/i.test(text))return 'paper_compare'
  return 'chat'
}
export function requestedSlides(text:string):number|undefined{
 const m=/(\d{1,2}|[二三四五六七八九十])\s*页/.exec(text);if(!m)return undefined
 const n=Number(m[1])||({'二':2,'三':3,'四':4,'五':5,'六':6,'七':7,'八':8,'九':9,'十':10} as Record<string,number>)[m[1]!]
 if(!n||n<2||n>12)throw new Error('当前汇报支持2–12页，请调整页数。');return n
}
