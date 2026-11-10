<script setup lang="ts">
import { ref } from 'vue'
import { useBackendStore,errorText } from '../stores/backend'
import { useResearchStore } from '../stores/research'
import Icon from './AppIcon.vue'
const backend=useBackendStore(),store=useResearchStore(),error=ref('')
async function attach(id:string){error.value='';try{const doc=await backend.attachLocal(id);if(doc)store.bindDocument(id,doc.id)}catch(e){error.value=errorText(e)}}
</script>
<template><div class="model-materials"><div v-if="backend.attached.length" class="model-material-chips"><span v-for="doc in backend.attached" :key="doc.id"><Icon name="FileText" :size="12"/><span>{{doc.name}}</span><button type="button" class="icon-button" :aria-label="`不再向模型提供 ${doc.name}`" :disabled="backend.busy||backend.attaching" @click="backend.attached=backend.attached.filter(d=>d.id!==doc.id)"><Icon name="X" :size="12"/></button></span></div><details><summary>选取提供给模型的 PDF（{{backend.attached.length}} / 5）</summary><p>仅勾选的材料上传到后端；发送时会把解析片段提供给当前模型，不代表读取了全部页面。</p><p v-if="!store.documents.length">请先通过下方「＋」上传本地 PDF。</p><button v-for="doc in store.documents" :key="doc.id" type="button" class="model-material-option" :disabled="backend.busy||backend.attaching" @click="attach(doc.id)"><Icon name="Plus" :size="13"/><span>{{doc.name}}</span></button><span v-if="backend.attaching" role="status">正在上传和解析材料…</span><p v-for="doc in backend.attached.filter(d=>d.parseStatus!=='text_ready')" :key="doc.id" class="settings-hint">{{doc.name}}：{{doc.parseStatus==='text_truncated'?'解析片段有限，回答可能只覆盖部分页面':'缺少可用文字，模型不能据此作全文分析'}}</p><p v-if="error" class="form-error" role="alert">{{error}}</p></details></div></template>
