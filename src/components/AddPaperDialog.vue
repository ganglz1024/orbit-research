<script setup lang="ts">
import {onMounted,onBeforeUnmount,ref} from 'vue'
import {useResearchStore} from '../stores/research'
import Icon from './AppIcon.vue'
import type {Paper} from '../types'
const props=defineProps<{paper?:Paper}>()
const emit=defineEmits<{close:[]}>(),store=useResearchStore(),dialog=ref<HTMLElement>(),title=ref(''),authors=ref(''),year=ref<number>(),url=ref(''),summary=ref(''),error=ref('')
let previous:HTMLElement|null=null
onMounted(()=>{if(props.paper){title.value=props.paper.title;authors.value=props.paper.authors;year.value=props.paper.year||undefined;url.value=props.paper.url||'';summary.value=props.paper.summary}previous=document.activeElement as HTMLElement;dialog.value?.querySelector('input')?.focus()})
onBeforeUnmount(()=>previous?.focus())
function save(){try{const data={title:title.value,authors:authors.value||'作者待补充',year:year.value||0,url:url.value,summary:summary.value};if(props.paper)store.updatePaper(props.paper.id,data);else store.addPaper({...data,source:'manual'});store.notify(props.paper?'论文资料已保存。':'论文已添加到当前空间。');emit('close')}catch(e){error.value=(e as Error).message}}
function keys(e:KeyboardEvent){if(e.key==='Escape')emit('close');if(e.key!=='Tab'||!dialog.value)return;const list=[...dialog.value.querySelectorAll<HTMLElement>('input,textarea,button')].filter(x=>!x.hasAttribute('disabled'));if(e.shiftKey&&document.activeElement===list[0]){e.preventDefault();list.at(-1)?.focus()}else if(!e.shiftKey&&document.activeElement===list.at(-1)){e.preventDefault();list[0]?.focus()}}
</script>
<template><div class="modal-scrim" @click.self="emit('close')"><section ref="dialog" class="upload-dialog add-paper-dialog" role="dialog" aria-modal="true" aria-labelledby="add-paper-title" @keydown="keys"><header><div><span class="eyebrow">YOUR RESEARCH MATERIALS</span><h2 id="add-paper-title">{{paper?"编辑论文资料":"添加自己的论文"}}</h2></div><button class="icon-button" aria-label="关闭添加论文" @click="emit('close')"><Icon name="X"/></button></header><form @submit.prevent="save"><div class="form-grid"><label class="full-row">论文标题<input v-model="title" required maxlength="500"/></label><label>作者<input v-model="authors" maxlength="2000"/></label><label>发表年份<input v-model.number="year" type="number" min="1000" max="2100"/></label><label class="full-row">原文链接<input v-model="url" type="url" maxlength="1000" placeholder="https://..."/></label><label class="full-row">摘要或阅读资料<textarea v-model="summary" rows="4" maxlength="4000" placeholder="仅填写你已取得的资料；可以留空后上传 PDF。"/></label></div><p v-if="error" class="form-error" role="alert">{{error}}</p><div class="settings-form-bottom"><span>保存你核对过的题名、作者、年份与摘要。</span><button class="primary-button" type="submit">{{paper?"保存资料":"添加到研究空间"}}</button></div></form></section></div></template>
