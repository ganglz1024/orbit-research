const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const net = require('node:net')
const { spawnSync } = require('node:child_process')

const root = path.resolve(__dirname, '..')
function compatible(version) {
  const [major, minor] = version.replace(/^v/, '').split('.').map(Number)
  return major > 22 || (major === 22 && minor >= 12) || (major === 20 && minor >= 19)
}

let runtime = process.execPath
if (!compatible(process.version)) {
  const executable = process.platform === 'win32' ? 'node.exe' : 'node'
  const candidates = [
    process.env.ORBIT_NODE_PATH,
    path.join(os.homedir(), '.cache', 'codex-runtimes', 'codex-primary-runtime', 'dependencies', 'node', 'bin', executable),
  ].filter(Boolean)
  runtime = candidates.find(candidate => {
    if (!fs.existsSync(candidate)) return false
    const result = spawnSync(candidate, ['--version'], { encoding: 'utf8' })
    return result.status === 0 && compatible(result.stdout.trim())
  })
  if (!runtime) {
    console.error('此项目需要 Node.js 20.19+ 或 22.12+。请更新 Node，或通过 ORBIT_NODE_PATH 指向兼容的 node 可执行文件。')
    process.exit(1)
  }
  console.log('当前 Node 版本较旧，已使用本机已有的兼容运行时。')
}

const vite = path.join(root, 'node_modules', 'vite', 'bin', 'vite.js')
const tsc = path.join(root, 'node_modules', 'vue-tsc', 'bin', 'vue-tsc.js')
const actions = {
  dev: [[vite, '--host', '127.0.0.1']],
  typecheck: [[tsc, '--noEmit']],
  build: [[tsc, '--noEmit'], [vite, 'build']],
  preview: [[vite, 'preview', '--host', '127.0.0.1']],
}
const jobs = actions[process.argv[2]]
if (!jobs) { console.error('未知的前端命令。'); process.exit(1) }
async function main() {
  if (process.argv[2] === 'dev') {
    const port = Number(process.env.ORBIT_FRONTEND_PORT || 5173)
    if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('ORBIT_FRONTEND_PORT 必须是 1–65535 之间的端口。')
    // Vite initializes its optimizer before checking the port; reject duplicate starts before that.
    await new Promise((resolve, reject) => {
      const probe = net.createServer()
      probe.once('error', error => reject(new Error(error.code === 'EADDRINUSE'
        ? `端口 ${port} 已被占用。如果 Orbit 已在运行，请直接打开 http://127.0.0.1:${port}，无需重复启动。`
        : error.message)))
      probe.listen(port, '127.0.0.1', () => probe.close(error => error ? reject(error) : resolve()))
    })
  }
  for (const args of jobs) {
    const result = spawnSync(runtime, args, { cwd: root, stdio: 'inherit', windowsHide: true })
    if (result.error) { console.error(result.error.message); process.exit(1) }
    if (result.status !== 0) process.exit(result.status || 1)
  }
}
main().catch(error => { console.error(error.message); process.exit(1) })
