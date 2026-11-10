const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const moduleUnderTest = { exports: {} }
const source = fs.readFileSync(path.resolve(__dirname, '../src/lib/promptInput.ts'), 'utf8')
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
new Function('module', 'exports', js)(moduleUnderTest, moduleUnderTest.exports)
const { submitsPrompt, promptAfterSend } = moduleUnderTest.exports
const enter = { key: 'Enter', shiftKey: false, ctrlKey: false, metaKey: false, altKey: false, isComposing: false, keyCode: 13 }
assert.equal(submitsPrompt(enter, false), true, 'plain Enter sends')
assert.equal(submitsPrompt({ ...enter, shiftKey: true }, false), false, 'Shift+Enter remains a newline')
for (const modifier of ['ctrlKey', 'metaKey', 'altKey']) assert.equal(submitsPrompt({ ...enter, [modifier]: true }, false), false, `${modifier} retains its existing behavior`)
assert.equal(submitsPrompt({ ...enter, isComposing: true }, false), false, 'IME confirmation cannot send')
assert.equal(submitsPrompt(enter, true), false, 'composition session cannot send')
assert.equal(submitsPrompt({ ...enter, keyCode: 229 }, false), false, 'IME compatibility key code cannot send')
assert.equal(promptAfterSend('保留这个问题', '保留这个问题', false), '保留这个问题', 'rejected submissions preserve the draft')
assert.equal(promptAfterSend('已经写了下一句', '刚刚提交的问题', true), '已经写了下一句', 'late completion cannot erase newer edits')
assert.equal(promptAfterSend('已接受的问题', '已接受的问题', true), '', 'only an accepted unchanged draft is cleared')
console.log('11 prompt input checks passed (IME guards, shortcuts, accepted/rejected drafts).')
