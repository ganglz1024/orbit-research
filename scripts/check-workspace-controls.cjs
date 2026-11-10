if(Number(process.versions.node.split('.')[0])<22){const p=require('path'),os=require('os'),cp=require('child_process');process.exit(cp.spawnSync(p.join(os.homedir(),'.cache','codex-runtimes','codex-primary-runtime','dependencies','node','bin','node.exe'),[__filename],{stdio:'inherit'}).status??1)}
const fs=require('fs'),ts=require('typescript'),assert=require('node:assert/strict'),{createPinia,setActivePinia}=require('pinia');
function load(file,deps={}){const m={exports:{}};new Function('require','module','exports',ts.transpileModule(fs.readFileSync(file,'utf8').replace(/if\(import\.meta\.hot\).*$/gm,''),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText)(id=>id in deps?deps[id]:require(id),m,m.exports);return m.exports}
const layout=load('src/lib/workspaceLayout.ts'),prune=load('src/lib/paperRemoval.ts'),state=new Map();let fail=false,count=0;const check=fn=>{fn();count++};
const items=[{id:'a'},{id:'b'},{id:'c'}];
check(()=>assert.deepEqual(layout.reorderWorkspace(items,'c','a').map(s=>s.id),['c','a','b']));
check(()=>assert.deepEqual(layout.reorderWorkspace(items,'a','c',true).map(s=>s.id),['b','c','a']));
check(()=>assert.equal(layout.reorderWorkspace(items,'a','a'),items));
check(()=>assert.equal(layout.reorderWorkspace(items,'x','b'),items));
check(()=>assert.deepEqual(items.map(s=>s.id),['a','b','c']));
check(()=>assert.equal(layout.clampAssistantWidth(-100,1200),300));
check(()=>assert.equal(layout.clampAssistantWidth(9000,1200),680));
check(()=>assert.equal(layout.clampAssistantWidth(9000,2000),720));
check(()=>assert.equal(layout.clampAssistantWidth(NaN,1200),300));
check(()=>assert.ok(1200-layout.clampAssistantWidth(800,1200)>=520));
const backend={busy:false,attaching:false};
const storage={readLocal:(k,f)=>state.has(k)?JSON.parse(state.get(k)):f,writeLocal:(k,v)=>{if(fail)throw Error('quota');state.set(k,JSON.stringify(v))}};
const {useResearchStore}=load('src/stores/research.ts',{'../lib/taskIntent':load('src/lib/taskIntent.ts'),'../lib/storage':storage,'../lib/workspaceLayout':layout,'../lib/legacyPresetCopy':{refreshPresetSessionCopy:x=>x,refreshPresetDeckCopy:x=>x},'../services/researchTasks':{},'../lib/paperSources':{},'../services/paperOrganization':{},'../lib/paperRemoval':prune,'./backend':{useBackendStore:()=>backend}});
setActivePinia(createPinia());let store=useResearchStore();const a=store.newSpace();store.startQuestion('Original research question');const paper=store.addPaper({title:'User paper',authors:'Author',year:2024,summary:'Evidence'});store.selectedIds=[paper.id];store.persist();
check(()=>assert.equal(store.renameSession(a,'  Research A  '),true));
check(()=>assert.equal(store.sessions[0].title,'Research A'));
store.persist();check(()=>assert.equal(store.sessions[0].title,'Research A'));check(()=>assert.equal(store.question,'Original research question'));
check(()=>assert.equal(store.renameSession(a,'  '),false));check(()=>assert.equal(store.renameSession(a,'X'.repeat(81)),false));
const b=store.newSpace();store.renameSession(b,'Research B');store.reorderSession(a,b);store.loadSession(b);store.persist();check(()=>assert.deepEqual(store.sessions.map(s=>s.id),[a,b]));
store.loadSession(a);check(()=>assert.equal(store.deleteSession(a),true));check(()=>assert.equal(store.activeId,b));check(()=>assert.equal(store.papers.length,1));check(()=>assert.deepEqual(store.deletedSessions[0].session.paperIds,[paper.id]));
check(()=>assert.equal(store.restoreSession(a),true));check(()=>assert.deepEqual(store.sessions.map(s=>s.id),[a,b]));check(()=>assert.equal(store.restoreSession(a),false));store.loadSession(a);check(()=>assert.deepEqual(store.selectedIds,[paper.id]));
fail=true;check(()=>assert.equal(store.renameSession(a,'Unstored'),false));check(()=>assert.equal(store.sessions[0].title,'Research A'));check(()=>assert.equal(store.deleteSession(a),false));fail=false;
backend.busy=true;check(()=>assert.equal(store.deleteSession(a),false));backend.busy=false;
setActivePinia(createPinia());store=useResearchStore();check(()=>assert.deepEqual(store.sessions.map(s=>s.title),['Research A','Research B']));
store.loadSession(a);store.deleteSession(a);store.deleteSession(b);check(()=>assert.equal(store.deletedSessions.length,2));check(()=>assert.equal(store.sessions.length,1));check(()=>assert.equal(store.visibleIds.length,0));check(()=>assert.ok(![a,b].includes(store.activeId)));
console.log(count+' workspace control checks passed (rename persistence, stable order, recoverable deletion, storage failure and width bounds).');process.exit(0)
