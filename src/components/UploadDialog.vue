<script setup lang="ts">
import { nextTick,onMounted,onUnmounted,ref } from 'vue'
import { useRouter } from 'vue-router'
import { useResearchStore } from '../stores/research'
import {useBackendStore} from '../stores/backend'
import {readerUrl} from '../lib/paperSources'
import Icon from './AppIcon.vue'
const emit=defineEmits<{close:[]}>(),store=useResearchStore(),router=useRouter(),dialog=ref<HTMLElement>(),input=ref<HTMLInputElement>(),dragging=ref(false),busy=ref(false)
const backend=useBackendStore()
const items=ref<{name:string;status:string;error?:string;id?:string}[]>([])
let previousFocus:HTMLElement|null=null
function uploadError(error:unknown){
  const message=error instanceof Error?error.message:'文件读取失败，请重试。'
  return /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Outdated Optimize Dep/i.test(message)
    ? 'PDF 阅读组件暂时未能加载，请刷新页面后重新选择文件。已保存的论文会保留。'
    : message
}
async function files(list:File[]){if(busy.value||store.organizingPapers||store.connectingPapers)return;if(list.length>5){store.notify('一次最多选择 5 份 PDF，请分批上传。');return}if(!list.length)return;items.value=list.map(file=>({name:file.name,status:'等待校验'}));busy.value=true
  const added:string[]=[]
  try{
    for(const [index,file]of list.entries()){const item=items.value[index]!;item.status='正在读取与校验';try{const {importPdf}=await import('../lib/pdf');const doc=await importPdf(file);item.id=doc.id;added.push(store.addPdfPaper(doc).id);item.status=`已保存到此浏览器 · ${doc.pageCount} 页`}catch(e){item.error=uploadError(e);item.status='上传未完成'}}
    await store.refreshDocuments()
    store.completedUpload={ids:items.value.flatMap(item=>item.id?[item.id]:[]),stamp:Date.now()}
    if(added.length&&store.autoOrganizePapers&&backend.modelMode){const organized=await store.organizePapers(added,false);const organizationErrors=store.processingError;const ready=store.activePapers.filter(p=>p.summary.trim()).map(p=>p.id);if(organized&&ready.length>=2){await store.connectSelected(ready);store.processingError=[organizationErrors,store.processingError].filter(Boolean).join('\n')}}
  }finally{busy.value=false}
}

function read(id:string){emit('close');router.push(readerUrl(id,store.activeId||'default'))}
function trap(e:KeyboardEvent){if(store.removalTarget)return;if(e.key==='Escape'){emit('close');return}if(e.key!=='Tab'||!dialog.value)return;const all=Array.from(dialog.value.querySelectorAll<HTMLElement>('button:not([disabled]),a,input:not([disabled]),[tabindex="0"]')).filter(x=>x.offsetParent!==null);if(!all.length)return;if(e.shiftKey&&document.activeElement===all[0]){e.preventDefault();all.at(-1)?.focus()}else if(!e.shiftKey&&document.activeElement===all.at(-1)){e.preventDefault();all[0]?.focus()}}
onMounted(()=>{previousFocus=document.activeElement as HTMLElement;nextTick(()=>dialog.value?.querySelector<HTMLButtonElement>('button')?.focus());document.addEventListener('keydown',trap)})
onUnmounted(()=>{document.removeEventListener('keydown',trap);previousFocus?.focus()})
</script>
<template><div class="modal-scrim" @click.self="emit('close')"><section ref="dialog" class="upload-dialog" role="dialog" aria-modal="true" aria-labelledby="upload-title"><header><div><span class="eyebrow">BRING YOUR OWN CURIOSITY</span><h2 id="upload-title">把论文，带进思考空间。</h2></div><button class="icon-button" aria-label="关闭文献上传" @click="emit('close')"><Icon name="X"/></button></header><p class="modal-description">上传后即可阅读、划线与批注。PDF 文件保存在当前浏览器。启用 AI 整理后，会将前 3 页的部分文字发送至你配置的模型。</p><label class="upload-ai-option"><input type="checkbox" :checked="store.autoOrganizePapers&&backend.modelMode" :disabled="!backend.modelMode||busy||store.organizingPapers||store.connectingPapers" @change="store.setAutoOrganize(($event.target as HTMLInputElement).checked)"/><span>上传后调用 AI 整理题名、作者、年份和主题联系<small v-if="!backend.modelMode">登录并配置模型后可启用；也可上传后在画布点击「AI 整理」。</small></span></label><p v-if="store.organizingPapers||store.connectingPapers" class="upload-ai-status" role="status">{{store.processingLabel}}</p><input ref="input" class="visually-hidden" type="file" accept="application/pdf,.pdf" multiple aria-label="选择 PDF 文献" @change="files(Array.from(($event.target as HTMLInputElement).files||[]));($event.target as HTMLInputElement).value=''"/><button class="drop-zone" :class="{dragging}" :disabled="busy||store.organizingPapers||store.connectingPapers" :aria-busy="busy" @click="input?.click()" @dragover.prevent="dragging=true" @dragleave="dragging=false" @drop.prevent="dragging=false;files(Array.from($event.dataTransfer?.files||[]))"><span class="upload-orb"><Icon name="FileUp" :size="30"/></span><strong>{{busy?'正在保存你的材料…':'拖拽 PDF 到这里，或点击选择'}}</strong><span>每份不超过 50 MB · 每次最多 5 份</span></button><div v-if="items.length" class="upload-results" aria-live="polite"><div v-for="item in items" :key="item.name"><Icon :name="item.error?'CircleHelp':item.id?'CheckCheck':'LoaderCircle'" :class="{'spin':!item.id&&!item.error}"/><div><strong>{{item.name}}</strong><small :class="{error:item.error}">{{item.error||item.status}}</small></div><button v-if="item.id" class="text-button" @click="read(item.id)">阅读<Icon name="ArrowUpRight" :size="14"/></button></div></div><div v-if="store.documents.length" class="existing-documents"><span class="section-mini-label">这个浏览器里的材料</span><div v-for="doc in store.documents" :key="doc.id" class="local-document-row"><button @click="read(doc.id)"><Icon name="FileText" :size="17"/><span>{{doc.name}}</span><small>{{doc.pageCount}} 页</small><Icon name="ArrowUpRight" :size="14"/></button><button class="icon-button" :aria-label="`删除浏览器文件 ${doc.name}`" :disabled="busy||store.organizingPapers||store.connectingPapers" @click="store.requestRemoval(doc.id,'document')"><Icon name="Trash2" :size="16"/></button></div></div><footer><span><Icon name="ShieldCheck" :size="14"/>仅本地存储，无云端备份</span></footer></section></div></template>
