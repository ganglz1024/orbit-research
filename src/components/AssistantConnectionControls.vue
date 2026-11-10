<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { useBackendStore,errorText } from '../stores/backend'
defineProps<{composer?:boolean}>()
const backend=useBackendStore(),error=ref(''),saving=ref(false)
onMounted(()=>backend.init())
async function update(key:'assistantMode'|'defaultSkillId'|'allowSkillTools',event:Event){saving.value=true;error.value='';try{const target=event.target as HTMLInputElement;await backend.setPreference({...backend.preference,[key]:key==='allowSkillTools'?target.checked:target.value})}catch(e){error.value=errorText(e)}finally{saving.value=false}}
</script>
<template><div class="assistant-connection"><div class="assistant-connection-row"><span>模型 API</span><RouterLink v-if="!backend.user||!backend.model?.configured" to="/settings">{{backend.user?'配置模型':'连接模型'}}</RouterLink><span v-else class="model-name" :title="backend.model.model">{{backend.model.model}}</span></div><template v-if="backend.modelMode"><label v-if="!composer">科研 Skill<select :value="backend.preference.defaultSkillId" :disabled="backend.busy||saving" @change="update('defaultSkillId',$event)"><option value="chat">通用研究对话</option><option v-for="skill in backend.runnableSkills" :key="skill.id" :value="skill.id">{{skill.name}}</option><option v-if="backend.preference.defaultSkillId!=='chat'&&!backend.runnableSkills.some(s=>s.id===backend.preference.defaultSkillId)" :value="backend.preference.defaultSkillId" disabled>原 Skill 已停用，请重新选择</option></select></label><label class="checkbox-label"><input type="checkbox" :checked="backend.preference.allowSkillTools" :disabled="backend.busy||saving" @change="update('allowSkillTools',$event)"/>允许按需调用自定义 Skill</label></template><p v-if="error" class="form-error" role="alert">{{error}}</p></div></template>
