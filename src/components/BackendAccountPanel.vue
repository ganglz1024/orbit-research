<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { useBackendStore,errorText } from '../stores/backend'
import { useResearchStore } from '../stores/research'
import Icon from './AppIcon.vue'
const backend=useBackendStore(),store=useResearchStore(),username=ref(''),password=ref(''),busy=ref(false),error=ref('')
onMounted(()=>backend.init())
async function submit(){busy.value=true;error.value='';try{await backend.login(username.value,password.value,backend.accountInitialized===false);password.value='';store.notify('已登录，配置与 Skills 已从后端加载。')}catch(e){error.value=errorText(e)}finally{busy.value=false}}
async function logout(){busy.value=true;try{await backend.logout()}catch(e){error.value=errorText(e)}finally{busy.value=false}}
</script>
<template>
  <section id="account" class="settings-section account-section">
    <div class="settings-section-heading"><span class="feature-icon violet"><Icon name="UserRound" :size="21"/></span><div><h2>{{backend.user?'已连接个人账户':'连接个人科研空间'}}</h2><p>{{backend.user?`${backend.user.username} · 配置、Skills 与模型对话保存在后端`:'登录后，使用已保存的模型 API 和自定义科研 Skill。'}}</p></div><button v-if="backend.user" class="text-button" :disabled="busy||backend.busy" @click="logout">退出登录</button></div>
    <p v-if="backend.error" class="form-error" role="alert">{{backend.error}} <button class="text-button" @click="backend.init(true)">重新连接</button></p>
    <p v-if="backend.loading" role="status">正在连接后端…</p>
    <form v-else-if="!backend.user&&backend.accountInitialized!==null" @submit.prevent="submit" :aria-busy="busy">
      <div class="form-grid"><label>账户名<input v-model="username" required autocomplete="username" pattern="[A-Za-z0-9_.@-]{3,64}" maxlength="64"/></label><label>密码<input v-model="password" required type="password" :minlength="backend.accountInitialized?1:12" maxlength="72" :autocomplete="backend.accountInitialized?'current-password':'new-password'"/></label></div>
      <p v-if="error" class="form-error" role="alert">{{error}}</p><div class="settings-form-bottom"><span>{{backend.accountInitialized?'使用后端个人账户登录':'首次使用：密码至少 12 个字符'}}</span><button class="primary-button" :disabled="busy"><Icon v-if="busy" name="LoaderCircle" class="spin" :size="15"/>{{backend.accountInitialized?'登录科研空间':'创建个人账户并登录'}}</button></div>
    </form>
  </section>
</template>
