import type { Deck,Message,Session } from '../types'

// Update only known, generated copy from older browser saves. User text stays intact.
const oldCover='研究阅读汇报\nOrbit · 前端演示内容，非模型生成'
const oldWelcome='示例研究空间已经展开。你可以从 RAG 的基本框架出发，沿着「检索质量」与「生成可靠性」两条线索阅读。点击任意论文卡片，就能查看来源和演示解读。'
const oldReading='可以按「研究问题 → 方法 → 证据 → 局限」组织阅读。当前回答为预置交互示例；真实的问题分析将在 Spring Boot 接入模型后提供。'
const oldSuffix='\n\n这是预置阅读示例。针对你提出的具体问题，需在接入模型后结合原文进一步核对。'
function refreshMessage(message:Message):Message {
  if(message.role!=='assistant')return message
  let content=message.content
  if(content===oldWelcome)content='研究空间已经展开。你可以从 RAG 的基本框架出发，沿着「检索质量」与「生成可靠性」两条线索阅读。点击任意论文卡片，就能查看来源和阅读提示。'
  else if(content===oldReading)content='可以按「研究问题 → 方法 → 证据 → 局限」组织阅读。当前为内置阅读提示，未调用模型。切换模型 API 后可针对具体问题继续分析。'
  else if(content.endsWith(oldSuffix))content=content.slice(0,-oldSuffix.length)+'\n\n以上为内置阅读提示，未调用模型。具体问题请切换模型 API，并结合原文核对。'
  return content===message.content?message:{...message,content}
}
export function refreshPresetSessionCopy(session:Session):Session {
  return {...session,messages:session.messages.map(refreshMessage)}
}
export function refreshPresetDeckCopy(deck:Deck):Deck {
  return {...deck,slides:deck.slides.map(slide=>slide.type==='cover'&&slide.body===oldCover?{...slide,body:'研究阅读汇报\nOrbit · 阅读草稿，非模型生成'}:slide)}
}
