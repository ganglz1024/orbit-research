<script setup lang="ts">
import { computed,ref,watch } from 'vue'
import Frame from '../components/AppFrame.vue'
import Icon from '../components/AppIcon.vue'
import AccountPanel from '../components/BackendAccountPanel.vue'
import { useResearchStore } from '../stores/research'
import { useBackendStore,errorText } from '../stores/backend'
import { backendApi,type ModelDraft,type ResearchPreference } from '../services/backendApi'
import { readLocal } from '../lib/storage'
const store=useResearchStore(),backend=useBackendStore(),error=ref(''),notice=ref(''),busy=ref(false),testing=ref(false),savingPreference=ref(false)
const draft=readLocal('orbit:connection-draft',{endpoint:'',model:''})
const settings=ref<ModelDraft>({baseUrl:draft.endpoint,model:draft.model,apiKey:'',clearApiKey:false,timeoutSeconds:120,maxOutputTokens:2048,includeUsage:false,tokenParameter:'max_tokens',revision:0})
const preference=ref<ResearchPreference>({...backend.preference})
const fields=['baseUrl','model','timeoutSeconds','maxOutputTokens','includeUsage','tokenParameter'] as const
const dirty=computed(()=>!!settings.value.apiKey||settings.value.clearApiKey||!backend.model||fields.some(k=>settings.value[k]!==backend.model![k]))
watch(()=>backend.model,value=>{if(value)settings.value={baseUrl:value.baseUrl,model:value.model,apiKey:'',clearApiKey:false,timeoutSeconds:value.timeoutSeconds,maxOutputTokens:value.maxOutputTokens,includeUsage:value.includeUsage,tokenParameter:value.tokenParameter,revision:value.revision}},{immediate:true})
watch(()=>backend.preference,value=>preference.value={...value},{immediate:true})
async function save(){busy.value=true;error.value='';notice.value='';try{backend.model=await backendApi.saveModel(settings.value);settings.value.apiKey='';notice.value='配置已保存；下次登录会自动恢复。';await backend.refreshCapabilities()}catch(e){error.value=errorText(e)}finally{busy.value=false}}
async function test(){testing.value=true;error.value='';notice.value='';try{const result=await backendApi.testModel();notice.value=`连接成功：${result.model}`;}catch(e){error.value=errorText(e)}finally{testing.value=false}}
async function savePreference(){savingPreference.value=true;error.value='';try{await backend.setPreference(preference.value);store.notify('研究偏好已保存，后续模型对话将使用这些配置。')}catch(e){error.value=errorText(e)}finally{savingPreference.value=false}}
</script>
<template>
  <Frame title="空间设置" subtitle="让工作台，贴合你的研究习惯。" eyebrow="MAKE THIS SPACE YOURS">
    <div class="settings-layout"><nav class="settings-nav" aria-label="设置类别"><a href="#account"><Icon name="UserRound" :size="17"/>个人账户</a><a href="#model" class="active"><Icon name="Sparkles" :size="17"/>模型与 API</a><a href="#preferences"><Icon name="SlidersHorizontal" :size="17"/>使用偏好</a><a href="#history">对话与历史</a><a href="#privacy"><Icon name="ShieldCheck" :size="17"/>本地资料</a></nav>
      <div class="settings-sections"><AccountPanel/>
        <section id="model" class="settings-section"><div class="settings-section-heading"><span class="feature-icon violet"><Icon name="Sparkles" :size="21"/></span><div><h2>连接你的研究助手</h2><p>支持 OpenAI 兼容的 Chat Completions 接口。</p></div><span class="connection-status">{{backend.model?.configured?'已配置':'待配置'}}</span></div>
          <form @submit.prevent="save" :aria-busy="busy||testing"><fieldset class="connection-fields" :disabled="!backend.user||busy||testing"><div class="form-grid">
            <label>接口协议<input value="OpenAI 兼容接口" readonly/></label><label>模型标识<input v-model="settings.model" required maxlength="200" placeholder="服务商提供的 model ID"/></label>
            <label class="full-row">API 基础地址<input v-model="settings.baseUrl" required type="url" maxlength="1000" placeholder="https://api.example.com/v1"/><small>填写基础地址，系统会追加 /chat/completions。</small></label>
            <label class="full-row">API Key<input v-model="settings.apiKey" type="password" autocomplete="off" maxlength="4096" :placeholder="backend.model?.hasApiKey?'已保存密钥；留空保留，输入新值替换':'粘贴服务商 API Key'"/><small>密钥在后端加密保存，读取配置时不会返回密钥。</small></label>
            <label v-if="backend.model?.hasApiKey" class="full-row checkbox-label"><input v-model="settings.clearApiKey" type="checkbox"/>清除已保存的 API Key</label>
          </div><details class="model-advanced"><summary>调用参数</summary><div class="form-grid"><label>超时时间（秒）<input v-model.number="settings.timeoutSeconds" type="number" min="5" max="300" required/></label><label>单轮最大输出 Token<input v-model.number="settings.maxOutputTokens" type="number" min="1" max="16384" required/></label><label>输出长度参数<select v-model="settings.tokenParameter"><option value="max_tokens">max_tokens</option><option value="max_completion_tokens">max_completion_tokens</option></select></label><label class="checkbox-label"><input v-model="settings.includeUsage" type="checkbox"/>接收用量统计（供应商需支持）</label></div></details>
          <div class="settings-form-bottom"><span>保存配置不会调用模型；测试连接会发送一条短请求。</span><div class="button-row"><button type="button" class="secondary-button" :disabled="dirty||!backend.model?.configured" @click="test"><Icon v-if="testing" name="LoaderCircle" class="spin" :size="15"/>测试已保存配置</button><button class="primary-button" :disabled="!dirty"><Icon v-if="busy" name="LoaderCircle" class="spin" :size="15"/>保存配置</button></div></div></fieldset></form>
          <p v-if="!backend.user" class="settings-hint">请先登录个人账户。</p><p v-if="error" class="form-error" role="alert">{{error}}</p><p v-if="notice" class="settings-hint" role="status">{{notice}}</p>
        </section>
        <section id="preferences" class="settings-section"><h2>为专注，留一点空间。</h2>
          <form v-if="backend.user" class="research-preference-form" @submit.prevent="savePreference"><div class="form-grid"><label class="full-row">记住我的研究背景与回答偏好<textarea v-model="preference.researchContext" rows="4" maxlength="4000" placeholder="例如：研究方向是检索增强生成；回答先给结论，再列证据与局限。"/><small>仅使用你主动保存的内容，每次模型对话都会参考；可随时清空。</small></label><label>研究助手默认模式<select v-model="preference.assistantMode"><option value="model">模型 API</option></select></label><label>默认科研 Skill<select v-model="preference.defaultSkillId"><option value="chat">通用研究对话</option><option v-for="s in backend.runnableSkills" :key="s.id" :value="s.id">{{s.name}}</option><option v-if="preference.defaultSkillId!=='chat'&&!backend.runnableSkills.some(s=>s.id===preference.defaultSkillId)" :value="preference.defaultSkillId" disabled>原 Skill 已停用，请重新选择</option></select></label><label class="full-row checkbox-label"><input v-model="preference.allowSkillTools" type="checkbox"/>允许模型按需调用已启用的自定义 Skill（每次最多提供 8 个）</label></div><div class="settings-form-bottom"><span>Skill 调用需要所选模型支持 tools；关闭后仍可手动指定 Skill。</span><button class="secondary-button" :disabled="savingPreference">保存研究偏好</button></div></form>
          <div class="preference-row"><div><strong>减少动态效果</strong><p>跳过论文逐张出现的等待，保留最终完整视图。</p></div><button role="switch" aria-label="减少动态效果" :aria-checked="store.reducedMotion" class="toggle-switch" :class="{on:store.reducedMotion}" @click="store.setReducedMotion(!store.reducedMotion)"><span></span></button></div>
          <div class="preference-row"><div><strong>暖灰雾紫</strong><p>暖灰底、近白卡片与柔和紫色。</p></div><span class="small-pill">当前主题</span></div><div class="preference-row desktop-font-preference"><div><strong>桌面字体</strong><p>Inter ＋ Noto Sans SC</p></div><span class="small-pill">当前方案</span></div>
        </section>
        <section id="history" class="settings-section"><h2>对话与历史</h2><p>模型对话、消息和来源快照保存在后端；浏览器研究空间保存画布与资料组织。删除对话不会删除独立笔记、论文或成果。</p><div class="button-row"><RouterLink class="secondary-button" to="/chat?history=active">管理历史对话</RouterLink><RouterLink class="secondary-button" to="/chat?history=deleted">最近删除的对话</RouterLink></div></section><section id="privacy" class="settings-section"><h2>你的资料，保存在这里。</h2><div class="privacy-metrics"><div><strong>{{store.documents.length}}</strong><span>本地文献</span></div><div><strong>{{store.decks.length}}</strong><span>汇报草稿</span></div><div><strong>{{store.sessions.length}}</strong><span>浏览器研究空间</span></div></div><div class="privacy-explanation"><Icon name="ShieldCheck" :size="20"/><p>PDF 与批注仍保存在浏览器；选为模型上下文的材料会上传至后端。模型模式会把消息、研究偏好、Skill 指令及明确选取的材料片段发送到配置的供应商。模型配置、Skills 和模型会话保存在后端数据库。</p></div></section>
      </div>
    </div>
  </Frame>
</template>
