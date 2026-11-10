<script setup lang="ts">
import { computed,onMounted,ref,watch } from 'vue'
import Frame from '../components/AppFrame.vue'
import Icon from '../components/AppIcon.vue'
import { useResearchStore } from '../stores/research'
import { useRouter } from 'vue-router'
import { useBackendStore,errorText } from '../stores/backend'
import { backendApi,type BackendSkill } from '../services/backendApi'
import SkillEditor from '../components/SkillEditor.vue'
import GithubSkillImport from '../components/GithubSkillImport.vue'
import {useComposerStore} from '../stores/composer'
const store=useResearchStore(),router=useRouter()
const backend=useBackendStore(),editing=ref<BackendSkill>(),editorOpen=ref(false),error=ref(''),pending=ref('')
const composer=useComposerStore()
onMounted(()=>backend.init())
const custom=computed(()=>backend.skills.filter(s=>s.kind==='custom'))
watch(()=>backend.skills,list=>{for(const s of list.filter(x=>x.kind==='builtin'))if(s.enabled===store.disabledSkills.includes(s.id))store.toggleSkill(s.id)},{immediate:true})
function disabled(id:string){return backend.skills.find(s=>s.id===id)?.enabled===false||(!backend.user&&store.disabledSkills.includes(id))}
async function toggle(id:string){pending.value=id;error.value='';try{if(backend.user){await backendApi.toggleSkill(id,!backend.skills.find(s=>s.id===id)?.enabled);await backend.refreshCapabilities()}else store.toggleSkill(id)}catch(e){error.value=errorText(e)}finally{pending.value=''}}
function edit(skill?:BackendSkill){editing.value=skill;editorOpen.value=true}
async function useSkill(s:BackendSkill){try{composer.forSpace(backend.user?.id||'guest',store.activeId||'default').skillId=s.id;await router.push('/chat?compose=1')}catch(e){error.value=errorText(e)}}
const academicCatalog=[
  {id:'ars_research',icon:'Compass',label:'研究',name:'ARS 深度研究',description:'梳理研究问题、检索策略与已有材料的证据。',inputs:'研究问题、明确引用的论文或 PDF',outputs:'问题拆解、检索式与证据缺口'},
  {id:'ars_write',icon:'Pencil',label:'写作',name:'ARS 学术写作',description:'根据提供的资料组织提纲与章节草稿。',inputs:'论文主题、笔记或草稿',outputs:'提纲与可编辑章节草稿'},
  {id:'ars_review',icon:'BookOpen',label:'评阅',name:'ARS 同行评阅',description:'根据提供的草稿进行模拟评阅并提出修订建议。',inputs:'明确提供的论文草稿',outputs:'带依据的模拟评阅与修订建议'},
  {id:'ars_pipeline',icon:'Layers',label:'流程',name:'ARS 研究流程',description:'规划研究到稿件修订的阶段与确认点。',inputs:'研究进度与已有材料',outputs:'阶段目标与人工确认点'},
  {id:'ars_screen',icon:'ShieldCheck',label:'筛选',name:'ARS 文献筛选',description:'按确认的纳排标准辅助筛选已提供的文献。',inputs:'纳排标准与明确引用的记录',outputs:'候选筛选意见及依据'},
]
const academicSkills=computed(()=>academicCatalog.map(item=>({...item,skill:backend.skills.find(s=>s.id===item.id)})))
const skills=[
  {id:'paper_search',icon:'Compass',color:'violet',label:'发现',name:'论文资料',description:'上传自己的论文，或手动添加已取得的论文元数据。',inputs:'论文标题、PDF、原文链接',outputs:'自己的论文卡片',action:'添加论文'},
  {id:'paper_read',icon:'BookOpen',color:'teal',label:'理解',name:'论文阅读助手',description:'把论文放在手边，把疑问留给对话。阅读、高亮、批注，一气呵成。',inputs:'本地 PDF、论文来源',outputs:'阅读提示、原文批注',action:'上传论文'},
  {id:'paper_compare',icon:'Layers',color:'blue',label:'思考',name:'多篇论文对比',description:'在不同方法之间搭起桥梁，从共同问题中发现差异和边界。',inputs:'选定的多篇论文',outputs:'比较维度、阅读问题',action:'去选择材料'},
  {id:'paper_metadata',icon:'Sparkles',color:'violet',label:'整理',name:'论文卡片整理',description:'读取 PDF 片段，提取题名、作者与年份，核对原文后更新卡片。',inputs:'本地 PDF、实际页序片段',outputs:'题录、AI 摘要、原文依据',action:'整理当前论文'},
  {id:'paper_relations',icon:'Network',color:'teal',label:'关联',name:'AI 主题联系',description:'根据已整理的论文资料分析整组联系，保留理由与不确定性。',inputs:'2–12 篇论文题录与摘要',outputs:'AI 主题连线、分析依据',action:'分析当前联系'},
  {id:'citation_graph',icon:'Network',color:'amber',label:'连接',name:'论文关系图谱',description:'把分散的论文放进同一片空间。点击一个节点，展开一条新的思路。',inputs:'论文节点、展开范围',outputs:'交互图谱、关系导出',action:'打开图谱'},
  {id:'ppt_generate',icon:'Presentation',color:'rose',label:'表达',name:'科研汇报',description:'让已经读过的材料，成为可以讲清楚的汇报。每一页都能继续推敲。',inputs:'选定材料、研究主题',outputs:'可编辑 PPTX、逐页预览',action:'生成汇报'},
]
async function run(id:string){if(id==='paper_search'){store.paperEditorOpen=true;router.push('/chat');return}if(id==='paper_read'){store.uploadOpen=true;return}if(id==='paper_metadata'||id==='paper_relations'){await router.push('/chat');if(id==='paper_metadata')await store.organizePapers();else await store.connectSelected();return}if(id==='ppt_generate'){const d=await store.createDeck();if(d)router.push('/artifacts/'+d.id);return}router.push(id==='citation_graph'?'/graphs':'/chat')}
</script>
<template>
  <Frame title="科研 Skills" subtitle="保存自己的研究方法，在需要时交给 AI 执行。" eyebrow="YOUR RESEARCH TOOLKIT">
    <div v-if="!backend.user" class="inline-info"><Icon name="ShieldCheck"/><p>登录个人账户后，可以创建和保存自定义 Skill。<RouterLink to="/settings#account">前往空间设置</RouterLink></p></div>
    <p v-if="backend.error||error" class="form-error" role="alert">{{error||backend.error}}</p>
    <div class="skills-summary"><span><Icon name="Sparkles" :size="17"/>为研究而生的能力</span><span>{{custom.length}} 个自定义 Skill<span class="context-dot">·</span>{{backend.user?'已连接个人账户':'内置能力可直接使用'}}</span></div>
    <GithubSkillImport/>
    <div class="skills-grid">
      <article v-for="skill in skills" :key="skill.id" class="skill-card" :class="{'skill-disabled':disabled(skill.id)}"><div class="skill-top"><span class="feature-icon" :class="skill.color"><Icon :name="skill.icon" :size="24"/></span><button role="switch" :aria-checked="!disabled(skill.id)" :aria-label="`${skill.name}能力`" :disabled="!!pending" class="toggle-switch" :class="{on:!disabled(skill.id)}" @click="toggle(skill.id)"><span></span></button></div><span class="skill-label">{{skill.label}} · {{backend.skills.find(s=>s.id===skill.id)?.status==='ready'?'模型可用':skill.id==='paper_read'||skill.id==='paper_compare'?'可接入模型':skill.id==='ppt_generate'?'模型生成与本地导出':'本地工具'}}</span><h2>{{skill.name}}</h2><p>{{skill.description}}</p><dl><div><dt>输入</dt><dd>{{skill.inputs}}</dd></div><div><dt>输出</dt><dd>{{skill.outputs}}</dd></div></dl><button v-if="backend.user&&backend.skills.find(s=>s.id===skill.id)?.status==='ready'" class="text-button" :disabled="disabled(skill.id)" @click="useSkill(backend.skills.find(s=>s.id===skill.id)!)">在模型对话中使用<Icon name="ArrowUpRight" :size="15"/></button><button v-else class="text-button" :disabled="disabled(skill.id)||(skill.id==='ppt_generate'&&store.generatingDeck)" @click="run(skill.id)">{{skill.id==='ppt_generate'&&store.generatingDeck?'正在生成汇报…':skill.action}}<Icon name="ArrowUpRight" :size="15"/></button></article>
      <article v-for="skill in custom" :key="skill.id" class="skill-card" :class="{'skill-disabled':!skill.enabled}"><div class="skill-top"><span class="feature-icon violet"><Icon name="Sparkles" :size="24"/></span><div class="button-row"><button class="icon-button" :aria-label="`编辑 ${skill.name}`" @click="edit(skill)"><Icon name="Pencil" :size="16"/></button><button role="switch" :aria-label="`${skill.name}能力`" :aria-checked="skill.enabled" :disabled="!!pending" class="toggle-switch" :class="{on:skill.enabled}" @click="toggle(skill.id)"><span></span></button></div></div><span class="skill-label">自定义 · v{{skill.version}}</span><h2>{{skill.name}}</h2><p>{{skill.description||'按你保存的科研步骤与输出要求进行分析。'}}</p><dl><div><dt>输入</dt><dd>研究问题、明确选择的材料</dd></div><div><dt>输出</dt><dd>遵循此 Skill 的模型回答</dd></div></dl><button class="text-button" :disabled="!skill.enabled" @click="useSkill(skill)">在对话中使用<Icon name="ArrowUpRight" :size="15"/></button></article>
      <article class="skill-coming"><span class="dashed-icon"><Icon name="Plus" :size="27"/></span><h2>你的工作方式，也是一种能力。</h2><p>定义分析步骤、证据要求与输出格式，保存为自己的科研 Skill。</p><button v-if="backend.user" class="secondary-button" @click="edit()"><Icon name="Plus" :size="16"/>创建自定义 Skill</button><RouterLink v-else class="secondary-button" to="/settings#account">登录后创建</RouterLink></article>
    </div>
    <section class="academic-skill-section" aria-labelledby="academic-skill-title">
      <h2 id="academic-skill-title">Academic Research Skills · Orbit 适配</h2>
      <p>将研究、写作、评阅、流程规划和文献筛选的方法接入当前模型对话。仅处理你明确提供的资料；原仓库的 Claude Code 多代理、脚本及外部检索不会在网站内自动运行。</p>
      <div class="skills-grid">
        <article v-for="item in academicSkills" :key="item.id" class="skill-card" :class="{'skill-disabled':item.skill?.enabled===false}">
          <div class="skill-top"><span class="feature-icon violet"><Icon :name="item.icon" :size="24"/></span><button role="switch" :aria-checked="item.skill?.enabled??true" :aria-label="`${item.skill?.name||item.label}能力`" :disabled="!backend.user||!!pending||!item.skill" class="toggle-switch" :class="{on:item.skill?.enabled??true}" @click="toggle(item.id)"><span></span></button></div>
          <span class="skill-label">{{item.label}} · {{!backend.user?'登录后使用':item.skill?.status==='ready'?'模型可用':'需配置模型'}}</span><h2>{{item.skill?.name||item.name}}</h2><p>{{item.skill?.description||item.description}}</p>
          <dl><div><dt>输入</dt><dd>{{item.inputs}}</dd></div><div><dt>输出</dt><dd>{{item.outputs}}</dd></div></dl>
          <button class="text-button" :disabled="!backend.modelMode||!item.skill?.enabled" @click="item.skill&&useSkill(item.skill)">在模型对话中使用<Icon name="ArrowUpRight" :size="15"/></button>
        </article>
      </div>
      <p class="settings-hint">来源：<a href="https://github.com/Imbad0202/academic-research-skills" target="_blank" rel="noopener noreferrer">academic-research-skills</a>（CC BY-NC 4.0）。这里是 Orbit 可执行的对话适配，并非原项目的完整 Claude Code 插件。</p>
    </section>
    <div class="inline-info"><Icon name="CircleHelp" :size="17"/><p>自定义 Skill 通过指令指导模型。手动指定即可使用；开启「按需调用」后，支持 tools 的模型也能自行选择。论文由用户上传或手动添加。精读、对比和汇报内容调用已配置模型；PPT 在浏览器内排版导出。学术检索与真实引用查询接口尚待接入。</p></div>
    <SkillEditor v-if="editorOpen" :skill="editing" @close="editorOpen=false" @saved="editorOpen=false;store.notify('Skill 已保存到个人账户。')"/>
  </Frame>
</template>
