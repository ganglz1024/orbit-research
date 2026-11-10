const fs=require('node:fs'),path=require('node:path'),ts=require('typescript'),assert=require('node:assert/strict')
const moduleUnderTest={exports:{}}
const js=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/lib/legacyPresetCopy.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText
new Function('module','exports',js)(moduleUnderTest,moduleUnderTest.exports)
const {refreshPresetDeckCopy,refreshPresetSessionCopy}=moduleUnderTest.exports
const cover='研究阅读汇报\nOrbit · 前端演示内容，非模型生成'
const deck={id:'deck',title:'人工标题',version:4,createdAt:'saved',slides:[{id:'cover',type:'cover',body:cover,sources:['paper']},{id:'edited',type:'cover',body:cover+'\n我的判断',sources:['paper']},{id:'content',type:'content',body:cover,sources:[]}]}
const updated=refreshPresetDeckCopy(deck)
assert.equal(updated.slides[0].body,'研究阅读汇报\nOrbit · 阅读草稿，非模型生成')
assert.equal(updated.slides[1].body,deck.slides[1].body,'custom cover text is untouched')
assert.equal(updated.slides[2].body,deck.slides[2].body,'ordinary slide content is untouched')
assert.deepEqual([updated.id,updated.title,updated.version,updated.createdAt,updated.slides[0].sources],['deck','人工标题',4,'saved',['paper']])
assert.equal(deck.slides[0].body,cover,'normalizing a view does not mutate the source record')
const old='可以按「研究问题 → 方法 → 证据 → 局限」组织阅读。当前回答为预置交互示例；真实的问题分析将在 Spring Boot 接入模型后提供。'
const session={id:'space',messages:[{id:'u',role:'user',content:old},{id:'a',role:'assistant',content:old},{id:'custom',role:'assistant',content:'演示实验是本文的一个主题。'}]}
const messages=refreshPresetSessionCopy(session).messages
assert.equal(messages[0].content,old,'user messages are untouched')
assert.equal(messages[1].content.includes('演示'),false)
assert.equal(messages[2].content,session.messages[2].content,'custom assistant content is untouched')
console.log('8 legacy preset copy checks passed (known templates only, user content and record identity preserved).')
