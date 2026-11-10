<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {useRouter} from 'vue-router'
import type { Paper } from '../types'
import { useResearchStore } from '../stores/research'
import { MICRO_INTERACTION } from '../config/microMotion'
import Icon from './AppIcon.vue'
import Minimap from './CanvasMinimap.vue'
import {canvasVisibleRect} from '../lib/canvasMinimap'
import {readerUrl} from '../lib/paperSources'
import {paperDisplayTitle} from '../lib/paperOrganization'
const props=withDefaults(defineProps<{mode?:'cards'|'graph';preview?:boolean}>(),{mode:'cards',preview:false})
const emit=defineEmits<{open:[id:string];relation:[ids:[string,string]]}>()
const router=useRouter()
const store=useResearchStore(),viewport=ref<HTMLElement>(),scale=ref(.8),offset=reactive({x:20,y:20})
function defaultPosition(p:Paper){
  const desktopRows:Record<string,number>={rag:170,'lost-middle':420,dpr:615,attention:635}
  return {x:p.x,y:!props.preview&&window.innerWidth>=1024?(desktopRows[p.id]??p.y):p.y}
}
const positions=reactive<Record<string,{x:number;y:number}>>({})
const viewportSize=reactive({width:1,height:1}),nodeSizes=reactive<Record<string,{width:number;height:number}>>({})
const visibleRect=computed(()=>canvasVisibleRect(offset,scale.value,viewportSize.width,viewportSize.height))
const minimapNodes=computed(()=>active.value.filter(p=>!!positions[p.id]).map(p=>({...positions[p.id]!,width:nodeSizes[p.id]?.width||264,height:nodeSizes[p.id]?.height||230,id:p.id,category:p.category,selected:store.selectedIds.includes(p.id)||activeNodeId.value===p.id})))
function navigate(point:{x:number;y:number}){offset.x=viewportSize.width/2-point.x*scale.value;offset.y=viewportSize.height/2-point.y*scale.value}
const active=computed(()=>store.activePapers)
const edges=computed(()=>store.edges.filter(([a,b])=>active.value.some(p=>p.id===a)&&active.value.some(p=>p.id===b)))
const focused=ref<string|null>(null)
const activeNodeId=computed(()=>focused.value??store.selectedPaperId)
const relatedIds=computed(()=>{
  const related=new Set<string>()
  for(const [a,b] of edges.value){
    if(a===activeNodeId.value&&b)related.add(b)
    if(b===activeNodeId.value&&a)related.add(a)
  }
  return related
})
let observer:ResizeObserver|undefined,nodeObserver:ResizeObserver|undefined,disposed=false
function observeNodes(){if(disposed||!viewport.value)return;nodeObserver?.disconnect();for(const node of viewport.value.querySelectorAll<HTMLElement>('[data-paper-id]')){nodeSizes[node.dataset.paperId!]={width:node.offsetWidth,height:node.offsetHeight};nodeObserver?.observe(node)}}
let observedWidth=0,observedHeight=0
const drag=ref<{type:'pan'|'node';id?:string;pointerId:number;x:number;y:number;ox:number;oy:number;moved:boolean}|null>(null)
let pointerTarget:HTMLElement|null=null,suppressPointerClick=false,lastDragEndedAt=0
function fit(){
  if(!viewport.value)return
  const {width,height}=viewport.value.getBoundingClientRect()
  if(width<1||height<1)return
  viewportSize.width=width;viewportSize.height=height
  const points=active.value.map(p=>positions[p.id]!)
  if(!props.preview&&window.innerWidth>=1024&&active.value.length){
    const bounds=active.value.map(p=>{
      const node=viewport.value!.querySelector<HTMLElement>(`[data-paper-id="${p.id}"]`)
      return {...positions[p.id]!,width:node?.offsetWidth||264,height:node?.offsetHeight||(props.mode==='graph'?110:230)}
    })
    const minX=Math.min(...bounds.map(p=>p.x))-12,minY=Math.min(...bounds.map(p=>p.y))-12
    const spanX=Math.max(...bounds.map(p=>p.x+p.width))-minX+12,spanY=Math.max(...bounds.map(p=>p.y+p.height))-minY+12
    const areaHeight=Math.max(180,height-52)
    scale.value=Math.min((width-44)/spanX,areaHeight/spanY,1.05)
    offset.x=(width-spanX*scale.value)/2-minX*scale.value
    offset.y=(areaHeight-spanY*scale.value)/2+8-minY*scale.value
    return
  }
  const minX=Math.min(0,...points.map(p=>p.x)),minY=Math.min(0,...points.map(p=>p.y))
  const maxX=Math.max(1030,...points.map(p=>p.x+260)),maxY=props.preview?660:Math.max(760,...points.map(p=>p.y+240))
  scale.value=Math.min((width-40)/(maxX-minX),(height-30)/(maxY-minY),1.15)
  offset.x=(width-(maxX-minX)*scale.value)/2-minX*scale.value;offset.y=(height-(maxY-minY)*scale.value)/2-minY*scale.value
}
function savePositions(ids:string[]){for(const id of ids){const p=store.paperById(id),point=positions[id];if(p&&point){p.x=point.x;p.y=point.y}}store.persist()}
function reset(){for(const [index,p] of active.value.entries())positions[p.id]={x:50+(index%3)*330,y:40+Math.floor(index/3)*270};savePositions(active.value.map(p=>p.id));fit()}
function zoom(direction:number){const old=scale.value;scale.value=Math.max(.38,Math.min(1.8,scale.value+direction*.1));const rect=viewport.value?.getBoundingClientRect();if(rect){offset.x=rect.width/2-(rect.width/2-offset.x)*scale.value/old;offset.y=rect.height/2-(rect.height/2-offset.y)*scale.value/old}}
function preparePointer(e:PointerEvent){if(e.isPrimary&&e.button===0&&!drag.value)suppressPointerClick=false}
function startPan(e:PointerEvent){
  if(!e.isPrimary||e.button!==0||drag.value||(e.target as Element).closest('button,a,.paper-node,.canvas-minimap'))return
  drag.value={type:'pan',pointerId:e.pointerId,x:e.clientX,y:e.clientY,ox:offset.x,oy:offset.y,moved:false}
  pointerTarget=e.currentTarget as HTMLElement
  pointerTarget.setPointerCapture(e.pointerId)
}
function startNode(e:PointerEvent,id:string){
  if(!e.isPrimary||e.button!==0||drag.value)return
  // Selection and expansion remain independent controls. The reading button also supports dragging.
  const control=(e.target as Element).closest('button,a,input,textarea,select,[contenteditable]')
  if(control&&!control.matches('.paper-open,.drag-handle'))return
  e.stopPropagation()
  drag.value={type:'node',id,pointerId:e.pointerId,x:e.clientX,y:e.clientY,ox:positions[id]!.x,oy:positions[id]!.y,moved:false}
  pointerTarget=e.currentTarget as HTMLElement
}
function move(e:PointerEvent){
  const current=drag.value
  if(!current||current.pointerId!==e.pointerId)return
  const dx=e.clientX-current.x,dy=e.clientY-current.y
  if(!current.moved){
    if(Math.hypot(dx,dy)<MICRO_INTERACTION.canvasDragThreshold)return
    current.moved=true
    suppressPointerClick=true
    // Capture only after intent is clear, so an ordinary button click keeps its native target.
    pointerTarget?.setPointerCapture(e.pointerId)
  }
  e.preventDefault()
  if(current.type==='pan'){
    offset.x=current.ox+dx;offset.y=current.oy+dy
  }else if(current.id){
    positions[current.id]!.x=current.ox+dx/scale.value
    positions[current.id]!.y=current.oy+dy/scale.value
  }
}
function endDrag(e?:PointerEvent){
  const current=drag.value
  if(!current||(e&&current.pointerId!==e.pointerId))return
  if(e?.type==='lostpointercapture'&&e.target!==pointerTarget)return
  const target=pointerTarget
  drag.value=null;pointerTarget=null
  if(current.moved)lastDragEndedAt=performance.now()
  if(current.type==='node'&&current.moved&&current.id)savePositions([current.id])
  if(target?.hasPointerCapture(current.pointerId))target.releasePointerCapture(current.pointerId)
}
function cancelDrag(){endDrag()}
function readOnDoubleClick(e:MouseEvent,paper:Paper){
  const control=(e.target as Element).closest('button,a,input,textarea,select,[contenteditable]')
  if(props.preview||drag.value||performance.now()-lastDragEndedAt<450||(control&&!control.matches('.paper-open')))return
  e.preventDefault();e.stopPropagation()
  if(paper.localDocumentId)void router.push(readerUrl(paper.localDocumentId,store.activeId||'default'))
  else store.notify('这篇论文尚未绑定 PDF，请先上传原文。')
}
function guardDragClick(e:MouseEvent){
  // A drag's compatibility click must not open a paper. Keyboard activation (detail=0) stays native.
  if(!suppressPointerClick||e.detail===0)return
  suppressPointerClick=false
  e.preventDefault();e.stopPropagation()
}
function keyMove(e:KeyboardEvent,id:string){const moves:Record<string,[number,number]>={ArrowLeft:[-16,0],ArrowRight:[16,0],ArrowUp:[0,-16],ArrowDown:[0,16]};if(moves[e.key]){e.preventDefault();positions[id]!.x+=moves[e.key]![0];positions[id]!.y+=moves[e.key]![1];savePositions([id])}}
function leaveFocus(e:FocusEvent){if(!(e.currentTarget as HTMLElement).contains(e.relatedTarget as Node|null))focused.value=null}
function focusNode(id:string){
  focused.value=id
  if(props.preview||window.innerWidth<1024)return
  nextTick(()=>{
    if(drag.value)return
    const area=viewport.value,node=area?.querySelector<HTMLElement>(`[data-paper-id="${id}"]`)
    if(!area||!node)return
    const a=area.getBoundingClientRect(),b=node.getBoundingClientRect(),margin=16,bottom=a.bottom-66
    if(b.left<a.left+margin)offset.x+=a.left+margin-b.left
    else if(b.right>a.right-margin)offset.x-=b.right-a.right+margin
    if(b.top<a.top+margin)offset.y+=a.top+margin-b.top
    else if(b.bottom>bottom)offset.y-=b.bottom-bottom
  })
}
function path(a:string,b:string){const p=positions[a]!,q=positions[b]!,desktop=window.innerWidth>=1024;const anchorX=desktop?(props.mode==='graph'?31:132):115,anchorY=props.mode==='graph'?(desktop?55:49):75;const sx=p.x+anchorX,sy=p.y+anchorY,tx=q.x+anchorX,ty=q.y+anchorY;const dx=Math.abs(tx-sx)*.48;return `M ${sx} ${sy} C ${sx+(tx>sx?dx:-dx)} ${sy}, ${tx+(tx>sx?-dx:dx)} ${ty}, ${tx} ${ty}`}
onMounted(()=>{nodeObserver=new ResizeObserver(entries=>{for(const entry of entries){const node=entry.target as HTMLElement,id=node.dataset.paperId;if(id&&node.isConnected)nodeSizes[id]={width:node.offsetWidth,height:node.offsetHeight}}});observeNodes();observer=new ResizeObserver(()=>{const r=viewport.value?.getBoundingClientRect();if(!r||r.width<1||r.height<1)return;if(Math.abs(r.width-observedWidth)<1&&Math.abs(r.height-observedHeight)<1)return;observedWidth=r.width;observedHeight=r.height;viewportSize.width=r.width;viewportSize.height=r.height;fit()});if(viewport.value)observer.observe(viewport.value);fit();document.fonts.ready.then(()=>{if(!disposed){observeNodes();fit()}});window.addEventListener('blur',cancelDrag)})
onUnmounted(()=>{disposed=true;observer?.disconnect();nodeObserver?.disconnect();window.removeEventListener('blur',cancelDrag);cancelDrag()})
watch(()=>props.mode,()=>nextTick(()=>{observeNodes();fit()}))
watch(()=>store.isSearching,value=>{if(!value)nextTick(fit)})
watch(()=>active.value.map(p=>p.id).join(','),()=>{for(const p of active.value)if(!positions[p.id])positions[p.id]=defaultPosition(p);nextTick(()=>{observeNodes();fit()})},{immediate:true})
defineExpose({fit,reset})
</script>
<template>
  <div ref="viewport" class="research-canvas" :class="{'canvas-preview':preview,'graph-mode':mode==='graph','is-dragging':drag?.moved}" @pointerdown.capture="preparePointer" @pointerdown="startPan" @pointermove="move" @pointerup="endDrag" @pointercancel="endDrag" @lostpointercapture="endDrag" @click.capture="guardDragClick">
    <div class="canvas-world" :style="{transform:`translate(${offset.x}px, ${offset.y}px) scale(${scale})`}">
      <svg class="canvas-connections" width="1100" height="830" aria-label="主题关联，非真实引用"><defs><linearGradient id="edge-gradient" x1="0" x2="1"><stop offset="0%" stop-color="var(--graph-line-active)"/><stop offset="100%" stop-color="var(--graph-line-end)"/></linearGradient></defs><g v-for="([a,b],index) in edges" :key="a+'-'+b"><path aria-hidden="true" :d="path(a!,b!)" :style="{'--edge-delay':`${index*70}ms`}" :class="{'edge-active':activeNodeId===a||activeNodeId===b}"/><path v-if="!preview" class="relation-hit" :d="path(a!,b!)" role="button" tabindex="0" :aria-label="`查看 ${active.find(p=>p.id===a)?.shortTitle} 与 ${active.find(p=>p.id===b)?.shortTitle} 的主题联系（非引用关系）`" @pointerdown.stop @click.stop="emit('relation',[a!,b!])" @keydown.enter.prevent="emit('relation',[a!,b!])" @keydown.space.prevent="emit('relation',[a!,b!])"/></g></svg>
      <TransitionGroup name="node">
        <article v-for="(paper,index) in active" :key="paper.id" :data-paper-id="paper.id" class="paper-node" :class="[paper.category,{'is-central':paper.id==='rag','is-selected':store.selectedIds.includes(paper.id),'is-focused':activeNodeId===paper.id,'is-related':relatedIds.has(paper.id),'is-node-dragging':drag?.type==='node'&&drag.moved&&drag.id===paper.id}]" :style="{left:positions[paper.id]!.x+'px',top:positions[paper.id]!.y+'px','--reveal-delay':preview?`${index*90}ms`:'0ms'}" @pointerdown="startNode($event,paper.id)" @dblclick="readOnDoubleClick($event,paper)" @mouseenter="focused=paper.id" @mouseleave="focused=null" @focusin="focusNode(paper.id)" @focusout="leaveFocus">
          <div v-if="mode==='cards'" class="paper-node-top"><span class="paper-category"><span></span>{{store.processingPaperId===paper.id?'正在整理…':paper.tag}}</span><div><button v-if="paper.metadataError&&!preview" class="node-select paper-metadata-warning" :aria-label="`重试整理 ${paper.shortTitle}：${paper.metadataError}`" :title="paper.metadataError+' 点击单篇重试'" :disabled="store.organizingPapers||store.connectingPapers" @click="store.organizePapers([paper.id],false)"><Icon name="RotateCw" :size="13"/></button><button v-if="!preview" class="node-select" :aria-label="`编辑 ${paper.shortTitle} 的资料`" @click="store.editPaper(paper.id)"><Icon name="Pencil" :size="12"/></button><button v-if="!preview" class="node-select" :aria-label="`${store.selectedIds.includes(paper.id)?'取消选择':'选择'} ${paper.shortTitle}`" :aria-pressed="store.selectedIds.includes(paper.id)" @click="store.togglePaper(paper.id)"><Icon :name="store.selectedIds.includes(paper.id)?'Check':'Plus'" :size="13"/></button><button v-if="!preview" class="node-select" :aria-label="`从当前空间移除 ${paper.shortTitle}`" @click="store.requestRemoval(paper.id,'paper')"><Icon name="Trash2" :size="12"/></button><button class="drag-handle" :aria-label="`移动 ${paper.shortTitle}，可使用方向键`" @keydown="keyMove($event,paper.id)"><Icon name="GripVertical" :size="14"/></button></div></div>
          <button class="paper-open" :title="paper.localDocumentId?'单击选中 · 双击阅读原文':'单击选中论文'" @click="emit('open',paper.id)" @keydown="keyMove($event,paper.id)">
            <span v-if="mode==='graph'" class="graph-node-orb"><Icon :name="paper.category==='foundation'?'BookOpen':paper.category==='retrieval'?'Network':'ShieldCheck'" :size="23"/></span>
            <strong :title="paper.title">{{paperDisplayTitle(paper)}}</strong><span class="paper-node-author"><span class="paper-author-names" :title="paper.authors">{{paper.authors}}</span><span class="paper-year">{{paper.year||'年份待补充'}}{{paper.yearBasis==='preprint'?' · 预印本':''}}</span></span>
            <span v-if="mode==='cards'" class="paper-node-description">{{paper.summary}}</span>
            <span v-if="mode==='cards'&&(!paper.localDocumentId||preview)" class="paper-node-footer"><span>{{paper.localDocumentId?'PDF':paper.url?'原文链接':'论文元数据'}}{{paper.metadataRunId?' · 题录已整理':''}}</span><Icon name="ArrowUpRight" :size="13"/></span>
          </button>
          <div v-if="mode==='cards'&&paper.localDocumentId&&!preview" class="paper-node-footer paper-reading-footer"><span>PDF{{paper.metadataRunId?' · 题录已整理':''}}</span><RouterLink :to="readerUrl(paper.localDocumentId,store.activeId||'default')" :aria-label="'阅读原文：'+paper.title" @pointerdown.stop @click.stop><Icon name="BookOpen" :size="13"/>阅读原文</RouterLink></div>
          <button v-if="mode==='graph'" class="graph-expand" :aria-label="`从 ${paper.shortTitle} 展开相关论文`" @click="store.expand()"><Icon name="Plus" :size="13"/></button>
          <button v-if="mode==='graph'&&!preview" class="node-select graph-node-select" :aria-label="`${store.selectedIds.includes(paper.id)?'取消选择':'选择'} ${paper.shortTitle}`" :aria-pressed="store.selectedIds.includes(paper.id)" @click="store.togglePaper(paper.id)"><Icon :name="store.selectedIds.includes(paper.id)?'Check':'Plus'" :size="13"/></button>
        </article>
      </TransitionGroup>
      <div v-if="!preview&&mode==='cards'&&active.length>3" class="canvas-sticky"><span>READING NOTE</span><p>从「找到了什么」<br>到「为什么有关系」。</p><div><span class="sticky-dot"></span>把阅读放回研究问题</div></div>
    </div>
    <div v-if="!preview" class="canvas-controls" aria-label="画布操作"><button aria-label="缩小画布" @click="zoom(-1)"><Icon name="Minus"/></button><span>{{Math.round(scale*100)}}%</span><button aria-label="放大画布" @click="zoom(1)"><Icon name="Plus"/></button><span class="control-divider"></span><button aria-label="整理并适配画布" title="整理画布" @click="reset"><Icon name="Maximize2"/></button><button aria-label="向左移动视图" @click="offset.x+=80"><Icon name="ChevronLeft"/></button><button aria-label="向右移动视图" @click="offset.x-=80"><Icon name="ChevronRight"/></button></div>
    <div v-if="!preview" class="canvas-help"><Icon name="MousePointer2" :size="13"/>拖动论文调整位置 · 拖动空白平移 · 单击阅读</div>
    <Minimap v-if="!preview" :nodes="minimapNodes" :view="visibleRect" @navigate="navigate" @fit="fit"/>
  </div>
</template>
