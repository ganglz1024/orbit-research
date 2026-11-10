<script setup lang="ts">
import { nextTick,onMounted,onUnmounted,defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import { useResearchStore } from './stores/research'
import Icon from './components/AppIcon.vue'
import { useControlTooltips } from './composables/useControlTooltips'
useControlTooltips()
const RemoveMaterialDialog=defineAsyncComponent(()=>import('./components/RemoveMaterialDialog.vue'))
const UploadDialog=defineAsyncComponent(()=>import('./components/UploadDialog.vue'))
const store=useResearchStore(),router=useRouter()
function shortcuts(e:KeyboardEvent){if(store.removalTarget)return;if((e.metaKey||e.ctrlKey)&&e.key==='k'){e.preventDefault();router.push('/chat/'+store.newSpace()).then(async()=>{await nextTick();document.querySelector<HTMLTextAreaElement>('#assistant-question')?.focus({preventScroll:true})})}if(e.key==='Escape'){store.uploadOpen=false;store.mobileMenu=false;store.selectedPaperId=null}}
onMounted(()=>{store.refreshDocuments();store.setReducedMotion(store.reducedMotion);window.addEventListener('keydown',shortcuts)})
onUnmounted(()=>window.removeEventListener('keydown',shortcuts))
</script>
<template>
  <a class="skip-link" href="#main-content">跳至主要内容</a>
  <RouterView/>
  <UploadDialog v-if="store.uploadOpen" @close="store.uploadOpen=false"/>
  <RemoveMaterialDialog v-if="store.removalTarget"/><Transition name="toast"><div v-if="store.toast" class="toast-message" role="status"><Icon name="CheckCheck"/><span>{{store.toast}}</span><button class="icon-button" aria-label="关闭提示" @click="store.toast=''"><Icon name="X" :size="16"/></button></div></Transition>
</template>
