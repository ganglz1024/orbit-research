const fs=require('node:fs'),ts=require('typescript'),assert=require('node:assert/strict');
if(Number(process.versions.node.split('.')[0])<22){const path=require('path'),os=require('os'),cp=require('child_process');process.exit(cp.spawnSync(path.join(os.homedir(),'.cache','codex-runtimes','codex-primary-runtime','dependencies','node','bin','node.exe'),[__filename],{stdio:'inherit'}).status??1)}
const {createPinia,setActivePinia}=require('pinia');
function load(file,deps={}){const m={exports:{}};const text=fs.readFileSync(file,'utf8').replace(/if\(import\.meta\.hot\).*$/gm,'');new Function('require','module','exports',ts.transpileModule(text,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,esModuleInterop:true}}).outputText)(id=>id in deps?deps[id]:require(id),m,m.exports);return m.exports}
const {contextPrompt,messageContext,referenceKey}=load('src/lib/composerContext.ts'),{parsePromptCommands}=load('src/lib/promptCommands.ts'),{renderResearchMarkdown:render}=load('src/lib/researchMarkdown.ts');
let count=0;const check=f=>{f();count++};
const snapshot={version:1,skill:'chat',sources:[{id:'paper-real',kind:'paper',name:'Title',scope:'metadata',body:'original snapshot'}]};
check(()=>assert.deepEqual(messageContext(contextPrompt('Question',snapshot)),{text:'Question',context:snapshot}));
check(()=>{const prompt=contextPrompt('Question',snapshot);snapshot.sources[0].body='edited later';assert.equal(messageContext(prompt).context.sources[0].body,'original snapshot')});
check(()=>assert.equal(messageContext('ordinary reply').context,null));
check(()=>assert.equal(messageContext('q\n\nORBIT_CONTEXT_V1\nnot json').context,null));
check(()=>assert.equal(messageContext('q\n\nORBIT_CONTEXT_V1\n{"version":1,"skill":"x","sources":[{}]}').context,null));
check(()=>assert.throws(()=>contextPrompt('a'.repeat(8000),snapshot),/8000/));
check(()=>assert.notEqual(referenceKey({kind:'note',id:'1'}),referenceKey({kind:'paper',id:'1'})));
check(()=>assert.equal(parsePromptCommands('/read @N explain',[{id:'paper_read',name:'论文解读',kind:'skill',aliases:['read']},{id:'note-id',name:'N',kind:'note'}]).mentions[0].id,'note-id'));
check(()=>assert.equal(parsePromptCommands('/compare',[{id:'paper_compare',name:'对比',kind:'skill',aliases:['compare']}]).skill.id,'paper_compare'));
check(()=>assert.throws(()=>parsePromptCommands('/missing',[]),/没有找到/));
check(()=>assert.ok(render('| A | B |\n|---|---|\n| 1 | 2 |').includes('<table>')));
check(()=>assert.ok(render('```java\nSystem.out.println(1);\n```').includes('language-java')));
check(()=>assert.ok(render('$x^2$').includes('katex')));
check(()=>assert.ok(render('\\[x^2\\]').includes('katex-display')));
check(()=>assert.ok(!render('<script>alert(1)</script>').includes('<script>')));
check(()=>assert.ok(!render('[bad](javascript:alert(1))').includes('href="javascript:')));
check(()=>assert.ok(!render('![secret](https://example.com/track)').includes('<img')));
check(()=>assert.ok(!render('$\\href{javascript:alert(1)}{x}$').includes('href="javascript:')));
const local=new Map(),backendApi={conversation:async id=>({messages:[{id:'m-'+id,role:'user',content:id,runId:'r',createdAt:''}]}),createConversation:async()=>({id:'fresh'}),createRun:async(id,body)=>{backendApi.lastBody=body;return{runId:'r1',run:{id:'r1',conversationId:id,status:'succeeded',answer:'answer',createdAt:''}}},subscribeRun:()=>()=>{},run:async()=>({status:'succeeded'}),conversations:async()=>({items:[{id:'known',title:'known'}],total:1}),cancelRun:async()=>({status:'cancelled'})};
const {useBackendStore}=load('src/stores/backend.ts',{'../lib/composerContext':{messageContext},'../services/backendApi':{backendApi,BackendApiError:class extends Error{}},'../lib/storage':{readLocal:(k,f)=>local.has(k)?JSON.parse(local.get(k)):f,writeLocal:(k,v)=>local.set(k,JSON.stringify(v)),getDocument:async()=>undefined}});
(async()=>{
setActivePinia(createPinia());const store=useBackendStore();store.user={id:'owner',username:'test'};store.model={configured:true};store.attached=[{id:'stale-pdf'}];await store.openConversation('known');check(()=>assert.equal(store.attached.length,0));check(()=>assert.equal(store.messages[0].content,'known'));
store.attached=[{id:'stale-pdf'}];await store.send('explicit source',{documentIds:['explicit-pdf'],skillId:'chat'});check(()=>assert.deepEqual(backendApi.lastBody.documentIds,['explicit-pdf']));
await store.listHistory();check(()=>assert.equal(store.conversationHistory[0].workspaceId,'default'));
store.attaching=true;await assert.rejects(store.openConversation('other'),/完成/);check(()=>assert.equal(store.conversationId,'known'));store.attaching=false;
let resolveOld;backendApi.conversation=id=>id==='slow'?new Promise(r=>resolveOld=r):Promise.resolve({messages:[{content:'latest'}]});const old=store.openConversation('slow');await store.openConversation('latest');resolveOld({messages:[{content:'stale'}]});await old;check(()=>assert.equal(store.messages[0].content,'latest'));

// Server search and lifecycle mutations must reconcile only the current query/session.
let resolveHistory;backendApi.conversations=(page,size,filters)=>filters.q==='old'?new Promise(r=>resolveHistory=r):Promise.resolve({items:[{id:'found',title:filters.q,workspaceId:'default'}],total:1});
store.historyQuery='old';const staleSearch=store.listHistory();await new Promise(r=>setTimeout(r,0));store.historyQuery='new';await store.listHistory();resolveHistory({items:[{id:'stale'}],total:99});await staleSearch;check(()=>assert.equal(store.conversationHistory[0].id,'found'));check(()=>assert.equal(store.historyTotal,1));
const current=store.conversationId;backendApi.manageConversation=async()=>{throw new Error('save failed')};await assert.rejects(store.manageConversation({id:current,revision:1},'delete'),/save failed/);check(()=>assert.equal(store.conversationId,current));check(()=>assert.equal(store.messages[0].content,'latest'));
backendApi.manageConversation=async()=>({});store.admitting=true;await assert.rejects(store.manageConversation({id:current},'delete'),/提交/);check(()=>assert.equal(store.conversationId,current));store.admitting=false;
await store.manageConversation({id:current},'delete');check(()=>assert.equal(store.conversationId,''));check(()=>assert.equal(store.messages.length,0));check(()=>assert.equal(store.attached.length,0));check(()=>assert.equal(store.spaceId,'default'));

store.conversationId='qa-current';store.messages=[{content:'keep'}];backendApi.manageConversation=async()=>{throw new Error('archive failed')};await assert.rejects(store.manageConversation({id:'qa-current'},'archive'),/archive failed/);check(()=>assert.equal(store.messages[0].content,'keep'));check(()=>assert.equal(store.conversationId,'qa-current'));
backendApi.manageConversation=async()=>({id:'qa-current'});backendApi.conversations=async()=>{throw new Error('list refresh failed')};await store.manageConversation({id:'qa-current'},'delete');check(()=>assert.equal(store.conversationId,''));check(()=>assert.match(store.historyError,/list refresh failed/));
const {useComposerStore}=load('src/stores/composer.ts');const composer=useComposerStore();composer.forSpace('owner','A').text='草稿';composer.forSpace('owner','A').references=[{id:'p',name:'paper',kind:'paper'}];check(()=>assert.equal(composer.forSpace('owner','A').text,'草稿'));check(()=>assert.equal(composer.forSpace('owner','B').text,''));check(()=>assert.equal(composer.forSpace('other','A').references.length,0));
// An accepted late request must stay in the originating conversation, not overwrite a newly opened PDF.
store.conversationId='source-reader-A';store.messages=[{role:'user',content:'old reader',runId:'old',createdAt:''}];store.attached=[];
let resolveAdmission,dispatch;const issued=new Promise(r=>dispatch=r);backendApi.createRun=(id,body)=>{backendApi.lastBody=body;dispatch();return new Promise(r=>resolveAdmission=r)};
const oldAdmission=store.send('late reading task',{documentIds:[],readingContext:{documentId:'pdf-a',documentVersion:'version-a',evidence:[]}});await issued;await store.switchChannel('reader:B');resolveAdmission({runId:'late',run:{id:'late',conversationId:'source-reader-A',status:'succeeded',answer:'must stay in A',createdAt:''}});await oldAdmission;
check(()=>assert.equal(store.channel,'reader:B'));check(()=>assert.equal(store.messages.length,0));check(()=>assert.equal(store.conversationId,''));check(()=>assert.deepEqual(backendApi.lastBody.documentIds,[]));check(()=>assert.equal(backendApi.lastBody.readingContext.documentId,'pdf-a'));
// Queue events must enrich execution metadata without erasing the user's original PDF snapshot.
store.conversationId='citation-reader';store.messages=[];let queuedEvent;
backendApi.createRun=async()=>({runId:'citation-run',run:{id:'citation-run',conversationId:'citation-reader',status:'running',answer:'',createdAt:''}});
backendApi.subscribeRun=(id,callback)=>{queuedEvent=callback;return()=>{}};
const reading={documentId:'pdf-citation',documentVersion:'hash-a',evidence:[{chunkId:'s1',pageIndex:1,quote:'actual selected text with trailing space ',scope:'selection',rects:[[1,2,3,2,1,4,3,4]]}]};
await store.send('question',{documentIds:[],readingContext:reading});
queuedEvent({eventId:1,type:'run_queued',data:{skillId:'paper_read',skillName:'论文解读',skillVersion:'1',model:'qa'}});
const user=store.messages.find(m=>m.role==='user');check(()=>assert.deepEqual(user.execution.reading,reading));check(()=>assert.equal(user.execution.skill.primary.id,'paper_read'));check(()=>assert.equal(user.execution.reading.evidence[0].quote.at(-1),' '));check(()=>assert.deepEqual(backendApi.lastBody.documentIds,[]));
await store.switchChannel('test-complete');
console.log(count+' Copilot composer checks passed (sources, aliases, Markdown safety, history races, explicit PDF scope and draft isolation).');process.exit(0)
})().catch(e=>{console.error(e);process.exit(1)});
