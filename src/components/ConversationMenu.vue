<script setup lang="ts">
import {nextTick,onBeforeUnmount,ref} from 'vue'
import Icon from './AppIcon.vue'
const props=defineProps<{title:string;items:{id:string;label:string}[]}>(),emit=defineEmits<{select:[id:string]}>()
const open=ref(false),trigger=ref<HTMLButtonElement>(),panel=ref<HTMLElement>(),style=ref({left:'0px',top:'0px'})
function close(focus=true){open.value=false;cleanup();if(focus)trigger.value?.focus()}
function outside(e:PointerEvent){if(!panel.value?.contains(e.target as Node)&&!trigger.value?.contains(e.target as Node))close(false)}
function place(){if(!trigger.value||!panel.value)return;const a=trigger.value.getBoundingClientRect(),p=panel.value;style.value={left:Math.max(8,Math.min(a.right-p.offsetWidth,innerWidth-p.offsetWidth-8))+'px',top:Math.max(8,Math.min(a.bottom+5,innerHeight-p.offsetHeight-8))+'px'}}
async function toggle(){if(open.value){close();return}open.value=true;await nextTick();place();panel.value?.querySelector('button')?.focus();document.addEventListener('pointerdown',outside,true);window.addEventListener('resize',place);window.addEventListener('scroll',place,true)}
function cleanup(){document.removeEventListener('pointerdown',outside,true);window.removeEventListener('resize',place);window.removeEventListener('scroll',place,true)}
function key(e:KeyboardEvent){if(e.key==='Escape'){e.preventDefault();e.stopPropagation();close()}else if(e.key==='Tab')close(false);else if(['ArrowDown','ArrowUp','Home','End'].includes(e.key)){e.preventDefault();const items=Array.from(panel.value?.querySelectorAll('button')||[]),i=items.indexOf(document.activeElement as HTMLButtonElement);items[e.key==='Home'?0:e.key==='End'?items.length-1:(i+(e.key==='ArrowDown'?1:-1)+items.length)%items.length]?.focus()}}
onBeforeUnmount(cleanup)
</script>
<template><button ref="trigger" class="icon-button" :aria-label="'管理对话 '+title" aria-haspopup="menu" :aria-expanded="open" @click.stop="toggle" @keydown.down.prevent="toggle"><Icon name="MoreHorizontal" :size="16"/></button><Teleport to="body"><div v-if="open" ref="panel" class="orbit-reasoning-menu history-action-menu" :style="style" role="menu" :aria-label="'对话操作 '+title" @keydown="key"><button v-for="item in items" :key="item.id" role="menuitem" @click="close();emit('select',item.id)">{{item.label}}</button></div></Teleport></template>
<style>.history-action-menu{width:180px}</style>
