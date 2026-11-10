const fs=require('node:fs'),path=require('node:path'),ts=require('typescript'),assert=require('node:assert/strict')
const tested={exports:{}}
new Function('module','exports',ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/lib/paperOrganization.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText)(tested,tested.exports)
const {verifyPaperMetadata,verifyPaperRelations,paperDisplayTitle}=tested.exports
const pages=[{page:1,text:'Evidence-Aware Language Models\nAlice Smith, Bob Chen\nPublished at Test Conference 2024. Reference: Other Work (2017). Abstract: A controlled source for validation.'}]
const valid={title:'Evidence-Aware Language Models',authors:['Alice Smith','Bob Chen'],year:2024,yearBasis:'publication',summary:'基于给定摘要的归纳。',evidence:[{field:'title',page:1,quote:'Evidence-Aware Language Models'},{field:'authors',page:1,quote:'Alice Smith, Bob Chen'},{field:'year',page:1,quote:'Published at Test Conference 2024.'}]}
let checks=0
function check(fn){fn();checks++}
const verify=raw=>verifyPaperMetadata(JSON.stringify(raw),pages)
check(()=>assert.equal(verify(valid).year,2024))
check(()=>assert.equal(verify(valid).authors,'Alice Smith, Bob Chen'))
check(()=>assert.equal(verify({...valid,yearBasis:'preprint'}).yearBasis,'publication'))
check(()=>assert.equal(verifyPaperMetadata(JSON.stringify({...valid,yearBasis:'publication',evidence:valid.evidence.map(e=>e.field==='year'?{...e,quote:'arXiv preprint 2024'}:e)}),[{page:1,text:pages[0].text+' arXiv preprint 2024'}]).yearBasis,'preprint'))
check(()=>assert.equal(verifyPaperMetadata('```json\n'+JSON.stringify(valid)+'\n```',pages).title,valid.title))
check(()=>assert.equal(verify({...valid,authors:['Invented Author']}).authors,undefined))
check(()=>assert.equal(verify({...valid,year:2030}).year,undefined))
check(()=>assert.equal(verify({...valid,year:2017,evidence:[...valid.evidence.filter(e=>e.field!=='year'),{field:'year',page:1,quote:'Reference: Other Work (2017).'}]}).year,undefined))
check(()=>assert.equal(verify({...valid,evidence:valid.evidence.map(e=>e.field==='authors'?{...e,page:999}:e)}).authors,undefined))
check(()=>assert.equal(verify({...valid,evidence:valid.evidence.map(e=>e.field==='title'?{...e,quote:'A fabricated title'}:e)}).title,undefined))
check(()=>assert.throws(()=>verify({...valid,evidence:[]}),/原文核对/))
check(()=>assert.throws(()=>verifyPaperMetadata('not json',pages),/有效 JSON/))
check(()=>assert.equal(paperDisplayTitle({title:'01_Transformer_Attention_Is_All_You_Need.pdf',localDocumentId:'a'}),'Transformer Attention Is All You Need'))
check(()=>assert.equal(paperDisplayTitle({title:'An_Actual_Manual_Title'}),'An_Actual_Manual_Title'))
const papers=Array.from({length:6},(_,i)=>({id:'paper-'+i})),edge={from:'paper-0',to:'paper-5',reason:'在提供的摘要中讨论相关主题，具体结论仍需核验。',kind:'topic'}
check(()=>assert.equal(verifyPaperRelations(JSON.stringify({edges:[edge]}),papers,'run-1')[0].runId,'run-1'))
check(()=>assert.equal(verifyPaperRelations(JSON.stringify({edges:[edge,{...edge,from:edge.to,to:edge.from}]}),papers,'run-1').length,1))
check(()=>assert.deepEqual(verifyPaperRelations('{"edges":[]}',papers,'run-1'),[]))
check(()=>assert.throws(()=>verifyPaperRelations(JSON.stringify({edges:[{...edge,to:'unknown'}]}),papers,'r'),/未知论文/))
check(()=>assert.throws(()=>verifyPaperRelations(JSON.stringify({edges:[{...edge,reason:''}]}),papers,'r'),/缺少依据/))
check(()=>assert.throws(()=>verifyPaperRelations(JSON.stringify({edges:[{...edge,to:edge.from}]}),papers,'r'),/未知论文/))
console.log(checks+' paper organization checks passed (PDF evidence, unknown fields, citation-year rejection, six-paper relations and invalid outputs).')
