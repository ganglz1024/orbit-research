const fs=require('node:fs'),path=require('node:path'),ts=require('typescript'),assert=require('node:assert/strict')
const tested={exports:{}}
const compiled=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/lib/promptCommands.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText
new Function('module','exports',compiled)(tested,tested.exports)
const {promptToken,insertPromptEntry,parsePromptCommands}=tested.exports
const entries=[{id:'skill1',name:'证据核验',kind:'skill'},{id:'skill2',name:'研究方法',kind:'skill'},{id:'pdf1',name:'论文 A.pdf',kind:'document'},{id:'paper1',name:'论文 A',kind:'paper'}]
assert.equal(promptToken('请用 /证',5).kind,'slash')
assert.equal(promptToken('https://example.com',19),null)
assert.equal(promptToken('user@example.com',16),null)
assert.equal(promptToken('比较 @论文',6).kind,'mention')
const inserted=insertPromptEntry('/证',promptToken('/证',2),entries[0]);assert.equal(inserted.text,'/[证据核验] ')
assert.equal(parsePromptCommands('/[证据核验] 请检查结论',entries).skill.id,'skill1')
assert.equal(parsePromptCommands('请 /[证据核验] 检查结论',entries).skill.id,'skill1')
assert.equal(parsePromptCommands('@[证据核验] 检查结论',entries).skill.id,'skill1')
assert.equal(parsePromptCommands('/[证据核验] 对比 @[论文 A.pdf] @[论文 A]',entries).mentions.length,2)
assert.equal(parsePromptCommands('@[论文 A.pdf] @[论文 A.pdf]',entries).mentions.length,1)
assert.throws(()=>parsePromptCommands('/[不存在] 检查',entries),/没有找到/)
assert.throws(()=>parsePromptCommands('/[证据核验] @[研究方法]',entries),/一个 Skill/)
assert.equal(insertPromptEntry('/',{start:0,end:1,kind:'slash',query:''},{id:'safe_id',name:'a]b',kind:'skill'}).text,'/[safe_id] ')
assert.equal(parsePromptCommands('/[证据核验]',entries).prompt,'请依据本次明确提供的材料执行所选 Skill。')
console.log('14 prompt command checks passed (slash and mentions, Chinese names, source binding, conflicts and caret insertion).')
