const fs=require('node:fs'),path=require('node:path'),ts=require('typescript'),assert=require('node:assert/strict')
const moduleUnderTest={exports:{}}
const js=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/lib/researchNote.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText
new Function('module','exports',js)(moduleUnderTest,moduleUnderTest.exports)
const evidence={sourceId:'a',excerptId:'p1',quote:'原文片段',page:1,kind:'original_support',explanation:'需核验条件'}
const result={summary:'归纳',details:'解释',evidence:[],comparison:[{dimension:'核心方法',cells:[{paperId:'a',text:'A|B\n方法',evidence:[evidence]}]}],commonalities:[],differences:[],directions:['待验证假设'],suggestions:[]}
const note={title:'研究笔记',body:'人工判断',createdAt:'now',revision:2,snapshot:{sources:[{id:'a',title:'论文|A',scope:'pdf_excerpt',url:'https://example.com'}],result}}
const markdown=moduleUnderTest.exports.researchNoteMarkdown(note)
assert.ok(markdown.includes('人工判断'))
assert.ok(markdown.includes('论文\\|A'))
assert.ok(markdown.includes('A\\|B 方法'))
assert.ok(markdown.includes('AI 推测，待验证'))
assert.ok(markdown.includes('PDF 第 1 页'))
assert.ok(markdown.includes('原文片段'))
assert.ok(markdown.includes('非全文覆盖声明'))
assert.ok(markdown.includes('笔记修订 2'))
console.log('8 research note export checks passed (table escaping, cell evidence, inference and scope).')
