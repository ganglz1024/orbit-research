<script setup lang="ts">
import {computed,nextTick,onMounted,onBeforeUnmount,ref} from 'vue'
import {useCopilotStore} from '../stores/copilot'
import { useRouter, useRoute } from 'vue-router'
import { useResearchStore } from '../stores/research'
import { useBackendStore } from '../stores/backend'
import Icon from './AppIcon.vue'
import Brand from './BrandMark.vue'
import { vMagnetic } from '../directives/pointerAccents'
const store=useResearchStore(),router=useRouter(),route=useRoute()
const backend=useBackendStore(),copilot=useCopilotStore()
const menuId=ref(''),renaming=ref(''),nameDraft=ref(''),deleteId=ref(''),trashOpen=ref(false),movingId=ref(''),overId=ref(''),after=ref(false),sessionList=ref<HTMLElement>()
const deletingBlocked=computed(()=>backend.busy||backend.attaching||copilot.busy)
let gesture:{id:string;y:number;pointer:number;target:HTMLElement}|undefined
function editName(id:string){menuId.value='';deleteId.value='';renaming.value=id;nameDraft.value=store.sessions.find(s=>s.id===id)?.title||'';nextTick(()=>{const input=sessionList.value?.querySelector<HTMLInputElement>('.session-rename-input');input?.focus();input?.select()})}
function saveName(){if(store.renameSession(renaming.value,nameDraft.value))renaming.value=''}
function remove(id:string){if(deletingBlocked.value)return;if(store.deleteSession(id)){deleteId.value='';menuId.value='';if(route.path.startsWith('/chat')||route.path.startsWith('/graphs'))void router.replace('/chat/'+store.activeId)}}
function beginMove(e:PointerEvent,id:string){if(e.button!==0||!e.isPrimary||renaming.value)return;gesture={id,y:e.clientY,pointer:e.pointerId,target:e.currentTarget as HTMLElement};gesture.target.setPointerCapture(e.pointerId);menuId.value='';deleteId.value='';e.preventDefault()}
function dragMove(e:PointerEvent){if(!gesture||gesture.pointer!==e.pointerId)return;if(!movingId.value&&Math.abs(e.clientY-gesture.y)<5)return;movingId.value=gesture.id;const row=document.elementFromPoint(e.clientX,e.clientY)?.closest<HTMLElement>('[data-session-id]');if(row&&sessionList.value?.contains(row)){overId.value=row.dataset.sessionId||'';const box=row.getBoundingClientRect();after.value=e.clientY>box.top+box.height/2}else overId.value='';const list=sessionList.value;if(list){const box=list.getBoundingClientRect();if(e.clientY<box.top+24)list.scrollTop-=16;else if(e.clientY>box.bottom-24)list.scrollTop+=16}e.preventDefault()}
function endMove(commit=true){const current=gesture;gesture=undefined;if(commit&&current&&movingId.value&&overId.value&&overId.value!==current.id){if(store.reorderSession(current.id,overId.value,after.value))store.notify('研究空间顺序已保存。')}movingId.value='';overId.value='';if(current?.target.hasPointerCapture(current.pointer))current.target.releasePointerCapture(current.pointer)}
function moveKey(e:KeyboardEvent,id:string){if(e.key==='Escape'){e.preventDefault();e.stopPropagation();endMove(false)}else if(['ArrowUp','ArrowDown'].includes(e.key)){e.preventDefault();if(store.moveSession(id,e.key==='ArrowUp'?-1:1))store.notify('研究空间顺序已保存。')}}
const cancelMove=()=>endMove(false)
onMounted(()=>window.addEventListener('blur',cancelMove))
onBeforeUnmount(()=>{endMove(false);window.removeEventListener('blur',cancelMove)})

function openSession(id:string){menuId.value='';deleteId.value='';store.loadSession(id);store.mobileMenu=false;router.push('/chat/'+id)}
function newSpace(){store.mobileMenu=false;router.push('/chat/'+store.newSpace())}
</script>
<template>
  <button v-if="store.mobileMenu" class="sidebar-scrim" aria-label="关闭导航" @click="store.mobileMenu=false"></button>
  <aside class="app-sidebar" :class="{'mobile-open':store.mobileMenu}">
    <RouterLink to="/" class="sidebar-brand" aria-label="研迹首页"><Brand/></RouterLink>
    <button v-magnetic class="new-space" @click="newSpace"><span class="magnetic-visual"><Icon name="Plus"/><span>新建研究空间</span><kbd>⌘ K</kbd></span></button>
    <nav class="primary-navigation" aria-label="主要导航">
      <RouterLink to="/chat" :class="{active:route.path.startsWith('/chat')||route.path.startsWith('/graphs')}" @click="store.mobileMenu=false"><Icon name="Compass"/>研究工作台</RouterLink>
      <button @click="store.uploadOpen=true;store.mobileMenu=false"><Icon name="FileText"/>会话材料<span v-if="store.documents.length" class="nav-count">{{store.documents.length}}</span></button>
      <RouterLink to="/skills" active-class="active" @click="store.mobileMenu=false"><Icon name="Sparkles"/>科研 Skills<span class="small-pill">{{backend.skills.length||5}}</span></RouterLink>
      <RouterLink to="/artifacts" active-class="active" @click="store.mobileMenu=false"><Icon name="Layers"/>成果中心</RouterLink>
    </nav>
    <div class="sidebar-section-label"><span>最近的研究</span><button class="icon-button" aria-label="查看已删除研究" :aria-expanded="trashOpen" @click="trashOpen=!trashOpen"><Icon name="MoreHorizontal" :size="16"/></button></div>
    <div ref="sessionList" class="recent-sessions managed-sessions" aria-label="研究空间列表">
      <div v-for="(session,index) in store.sessions" :key="session.id" :data-session-id="session.id" class="session-entry" :class="{selected:store.activeId===session.id,'is-sorting':movingId===session.id,'drop-before':overId===session.id&&movingId!==session.id&&!after,'drop-after':overId===session.id&&movingId!==session.id&&after}">
        <div v-if="renaming===session.id" class="session-rename"><label class="visually-hidden" :for="'rename-'+session.id">研究空间名称</label><input :id="'rename-'+session.id" v-model="nameDraft" class="session-rename-input" maxlength="80" @keydown.enter.prevent="saveName" @keydown.esc.stop.prevent="renaming=''"/><button class="icon-button" aria-label="保存空间名称" @click="saveName"><Icon name="Check" :size="14"/></button><button class="icon-button" aria-label="取消重命名" @click="renaming=''"><Icon name="X" :size="14"/></button></div>
        <div v-else class="session-entry-main"><button class="session-grip" :aria-label="'拖动排序 '+session.title+'，可用上下方向键'" title="拖动调整顺序 · 上下方向键移动" @pointerdown="beginMove($event,session.id)" @pointermove="dragMove" @pointerup="endMove()" @pointercancel="endMove(false)" @lostpointercapture="endMove(false)" @keydown="moveKey($event,session.id)"><Icon name="GripVertical" :size="14"/></button><button class="session-open" :title="session.title" :aria-current="store.activeId===session.id?'page':undefined" @click="openSession(session.id)"><Icon name="MessageSquare" :size="14"/><span>{{session.title}}</span></button><button class="session-more icon-button" :aria-label="'管理研究空间 '+session.title" :aria-expanded="menuId===session.id" @click="menuId=menuId===session.id?'':session.id;deleteId=''" @keydown.esc.stop="menuId=''"><Icon name="MoreHorizontal" :size="14"/></button></div>
        <div v-if="menuId===session.id" class="session-actions" role="group" @keydown.esc.stop.prevent="menuId='';deleteId=''" :aria-label="session.title+' 的空间操作'"><button @click="editName(session.id)">重命名</button><button :disabled="index===0" @click="store.moveSession(session.id,-1)">上移</button><button :disabled="index===store.sessions.length-1" @click="store.moveSession(session.id,1)">下移</button><button :disabled="deletingBlocked" @click="deleteId=session.id">删除</button><div v-if="deleteId===session.id" class="session-delete-confirm"><p>移到已删除研究？可以恢复，PDF、批注及后端笔记和对话仍保留。</p><button :disabled="deletingBlocked" @click="remove(session.id)">确认删除研究空间</button><button @click="deleteId=''">取消</button></div></div>
      </div>
      <button v-if="!store.sessions.length" @click="router.push('/chat')"><span class="session-dot"></span><span>开始新的研究</span></button><span v-if="!store.sessions.length" class="sidebar-hint">从一个好问题开始。</span>
      <section v-if="trashOpen" class="deleted-session-list" aria-label="已删除研究"><strong>已删除研究</strong><p v-if="!store.deletedSessions.length">暂无已删除的研究空间。</p><div v-for="item in store.deletedSessions" :key="item.session.id"><span :title="item.session.title">{{item.session.title}}</span><button :aria-label="'恢复 '+item.session.title" @click="store.restoreSession(item.session.id)">恢复</button></div></section>
    </div>
    <div class="sidebar-bottom">
      <div class="local-note"><span class="status-dot"></span><div>你的思考，留在这里<small>{{backend.user?'模型配置与 Skills 已连接后端':'资料仅存此浏览器'}}</small></div></div>
      <RouterLink class="profile-row" to="/settings" @click="store.mobileMenu=false"><span class="avatar">{{backend.user?.username.slice(0,1).toUpperCase()||'?'}}</span><div>{{backend.user?backend.user.username+' 的空间':'未登录'}}<small>{{backend.user?'个人研究工作台':'登录以连接模型'}}</small></div><Icon name="Settings2" :size="17"/></RouterLink>
    </div>
  </aside>
</template>
