<script setup lang="ts">
import {onMounted,onBeforeUnmount,ref,nextTick} from 'vue'
import {useResearchStore} from '../stores/research'
const store=useResearchStore(),panel=ref<HTMLElement>()
let previous:HTMLElement|null=null
function close(){if(!store.removing)store.removalTarget=null}
function key(event:KeyboardEvent){
  if(event.key==='Escape'){event.preventDefault();event.stopImmediatePropagation();close()}
  if(event.key!=='Tab'||!panel.value)return
  const items=Array.from(panel.value.querySelectorAll<HTMLButtonElement>('button:not([disabled])'))
  if(!items.length){event.preventDefault();return}
  if(event.shiftKey&&document.activeElement===items[0]){event.preventDefault();items.at(-1)?.focus()}
  else if(!event.shiftKey&&document.activeElement===items.at(-1)){event.preventDefault();items[0]?.focus()}
}
onMounted(()=>{previous=document.activeElement as HTMLElement;nextTick(()=>panel.value?.querySelector('button')?.focus());document.addEventListener('keydown',key,true)})
onBeforeUnmount(()=>{document.removeEventListener('keydown',key,true);if(previous?.isConnected)previous.focus()})
</script>
<template><div class="modal-scrim material-removal-scrim" @click.self="close"><section ref="panel" class="upload-dialog" role="alertdialog" aria-modal="true" aria-labelledby="remove-material-title" aria-describedby="remove-material-description"><h2 id="remove-material-title">{{store.removalTarget?.kind==='document'?'删除浏览器中的 PDF？':'从当前空间移除论文？'}}</h2><p class="removal-name">{{store.removalTarget?.name}}</p><p id="remove-material-description">{{store.removalTarget?.kind==='document'?'将删除本浏览器保存的 PDF 及其批注，并移除所有研究空间中对应的卡片、选中状态和连线。此操作无法撤销，请先导出需要保留的批注。已保存的笔记、成果及后端文件不会删除。':'只移除当前研究空间中的卡片与相关连线；浏览器 PDF、批注及其他研究空间不受影响，可从会话材料重新添加。'}}</p><p v-if="store.removalError" role="alert" class="form-error">{{store.removalError}}</p><div class="button-row"><button class="secondary-button" :disabled="store.removing" @click="close">取消</button><button class="primary-button" :disabled="store.removing" @click="store.confirmRemoval()">{{store.removing?'正在处理…':store.removalTarget?.kind==='document'?'确认删除 PDF 与批注':'确认移除卡片'}}</button></div></section></div></template>
