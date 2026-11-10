<script setup lang="ts">
import {ref} from 'vue'
import {useBackendStore,errorText} from '../stores/backend'
import {backendApi} from '../services/backendApi'
const backend=useBackendStore(),url=ref(''),busy=ref(false),error=ref(''),notice=ref(''),source=ref('')
async function install(){if(busy.value)return;busy.value=true;error.value='';notice.value='';try{const result=await backendApi.importGithubSkill(url.value.trim());await backend.refreshCapabilities();notice.value=(result.reused?'该版本已存在：':'已安装：')+result.skill.name+' · 版本 '+result.commitSha.slice(0,8);source.value=result.sourceUrl}catch(e){error.value=errorText(e)}finally{busy.value=false}}
</script>
<template><section class="github-skill-import"><h2>从 GitHub 安装科研 Skill</h2><p>提供公开仓库、Skill 目录或 SKILL.md 链接。也可以在研究助手中发送“安装 Skill + GitHub 链接”，让模型调用安装工具。</p><form @submit.prevent="install" :aria-busy="busy"><label for="github-skill-url">GitHub 链接</label><div class="button-row"><input id="github-skill-url" v-model="url" required type="url" placeholder="https://github.com/owner/repo/tree/main/skill" maxlength="1000" :disabled="busy||!backend.user"/><button class="primary-button" :disabled="busy||!backend.user||!url.trim()">{{busy?'正在获取并安装…':'安装到个人 Skills'}}</button></div></form><small>导入 Markdown 指令及最多 3 份相对 Markdown 资料；记录仓库版本，不执行脚本或安装系统依赖。</small><p v-if="!backend.user" class="settings-hint">请先登录个人账户。</p><p v-if="error" class="form-error" role="alert">{{error}}</p><p v-if="notice" class="settings-hint" role="status">{{notice}} <a :href="source" target="_blank" rel="noopener noreferrer">查看固定版本来源 ↗</a></p></section></template>
