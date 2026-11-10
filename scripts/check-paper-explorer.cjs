// Pure input/physics checks; no browser or extra test runner is required.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const loaded = new Map()
function loadSource(filename) {
  if (loaded.has(filename)) return loaded.get(filename)
  const module = { exports: {} }
  const js = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const localRequire = name => {
    assert.ok(name.startsWith('.'), 'Only local motion/config modules belong in these checks')
    return loadSource(path.resolve(path.dirname(filename), `${name}.ts`))
  }
  new Function('require', 'module', 'exports', js)(localRequire, module, module.exports)
  loaded.set(filename, module.exports)
  return module.exports
}
const { PaperPanMotion } = loadSource(path.resolve(__dirname, '../src/lib/paperPanMotion.ts'))
let passed = 0
function check(name, action) { action(); passed++; console.log(`PASS ${name}`) }
function settle(motion, dt = 16) {
  for (let i = 0; i < 1000; i++) if (!motion.step(dt)) return
  assert.fail('Inertia failed to stop')
}

check('small pointer jitter remains a click', () => {
  const m = new PaperPanMotion()
  m.begin(100, 100, 0, 1)
  assert.equal(m.move(103, 103, 20), false)
  assert.equal(m.end(21), false)
  assert.equal(m.x, 0); assert.equal(m.y, 0)
})
check('diagonal gesture exceeds threshold and is classified as a drag', () => {
  const m = new PaperPanMotion()
  m.begin(100, 100, 0, 1)
  assert.equal(m.move(106, 106, 20), true)
  assert.equal(m.end(21), true)
  assert.equal(m.x, 6); assert.equal(m.y, 6)
})
check('scaled canvas preserves direct pointer tracking', () => {
  const m = new PaperPanMotion()
  m.begin(100, 100, 0, 0.5)
  m.move(125, 115, 30)
  assert.equal(m.x, 50); assert.equal(m.y, 30)
})
check('finite bounds absorb a very large drag and settle without crossing to the other edge', () => {
  const m = new PaperPanMotion()
  m.begin(0, 0, 0, 1); m.move(10000, -10000, 20); m.end(21)
  assert.ok(m.x <= 134 && m.y >= -92)
  settle(m)
  assert.equal(m.x, 110); assert.equal(m.y, -68)
})
check('release coasts forward and stops naturally', () => {
  const m = new PaperPanMotion()
  m.begin(0, 0, 0, 1); m.move(12, 0, 64); m.end(65)
  m.step(16)
  assert.ok(m.x > 12)
  settle(m)
  const resting = m.x
  assert.equal(m.step(16), false)
  assert.equal(m.x, resting)
})
check('60 Hz and 120 Hz inertia travel approximately the same distance', () => {
  const a = new PaperPanMotion(), b = new PaperPanMotion()
  for (const m of [a, b]) { m.begin(0, 0, 0, 1); m.move(12, 0, 64); m.end(65) }
  settle(a, 1000 / 60); settle(b, 1000 / 120)
  assert.ok(Math.abs(a.x - b.x) < 0.3)
})
check('leaving/re-entering the scroll scene retains the explored position', () => {
  const m = new PaperPanMotion()
  m.nudge(48, -30); m.cancel()
  assert.equal(m.x, 48); assert.equal(m.y, -30)
  m.begin(100, 100, 0, 1); m.move(112, 108, 30); m.end(31)
  assert.equal(m.x, 60); assert.equal(m.y, -22)
  m.reset(); assert.equal(m.x, 0); assert.equal(m.y, 0)
})
check('short viewports constrain repeated buttons and hard drags to visible asymmetric room', () => {
  const m = new PaperPanMotion()
  m.setVerticalBounds(-14, 32)
  m.nudge(0, -1000); assert.equal(m.y, -14)
  m.nudge(0, 1000); assert.equal(m.y, 32)
  m.begin(0, 0, 0, 1); m.move(0, -10000, 20); m.end(21)
  for (let i = 0; i < 500; i++) { m.step(16); assert.ok(m.y >= -14 && m.y <= 32) }
  assert.equal(m.y, -14)
})
check('extra vertical room retains elastic overshoot and the original resting range', () => {
  const m = new PaperPanMotion()
  m.setVerticalBounds(-160, 180)
  m.begin(0, 0, 0, 1); m.move(0, 10000, 20); m.end(21)
  assert.ok(m.y > 68 && m.y <= 92)
  settle(m); assert.equal(m.y, 68)
})
check('resize clamps only unavailable vertical room and preserves horizontal exploration', () => {
  const m = new PaperPanMotion()
  m.nudge(48, -60)
  m.setVerticalBounds(-18, 30)
  assert.equal(m.y, -18); assert.equal(m.x, 48)
  m.setVerticalBounds(-150, 150)
  assert.equal(m.y, -18)
  m.nudge(0, -48); assert.equal(m.y, -66)
})
console.log(`${passed} paper explorer motion checks passed.`)
