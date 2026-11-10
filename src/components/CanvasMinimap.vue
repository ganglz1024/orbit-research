<script setup lang="ts">
import {computed,onMounted,onBeforeUnmount,reactive,ref,shallowRef} from 'vue'
import {minimapProjection,projectMinimapRect,minimapWorldPoint,type CanvasRect,type CanvasPoint,type MinimapProjection} from '../lib/canvasMinimap'
const props=defineProps<{nodes:Array<CanvasRect&{id:string;category:string;selected:boolean}>;view:CanvasRect}>()
const emit=defineEmits<{navigate:[point:CanvasPoint];fit:[]}>()
const svg=ref<SVGSVGElement>(),size=reactive({width:114,height:83}),frozen=shallowRef<MinimapProjection|null>(null)
const projection=computed(()=>frozen.value||minimapProjection(props.nodes,props.view,size.width,size.height))
const nodes=computed(()=>props.nodes.map(node=>({...node,rect:projectMinimapRect(node,projection.value)})))
const visible=computed(()=>projectMinimapRect(props.view,projection.value))
let observer:ResizeObserver|undefined,gesture:{pointer:number;start:CanvasPoint;center:CanvasPoint;target:SVGSVGElement}|undefined
function point(e:PointerEvent):CanvasPoint{const rect=svg.value!.getBoundingClientRect();return{x:(e.clientX-rect.left)*size.width/rect.width,y:(e.clientY-rect.top)*size.height/rect.height}}
function begin(e:PointerEvent){
  if(e.button!==0||!e.isPrimary||!svg.value)return
  frozen.value=projection.value
  const mini=point(e),world=minimapWorldPoint(mini,projection.value),view=visible.value
  const inside=mini.x>=view.x&&mini.x<=view.x+view.width&&mini.y>=view.y&&mini.y<=view.y+view.height
  const center=inside?{x:props.view.x+props.view.width/2,y:props.view.y+props.view.height/2}:world
  gesture={pointer:e.pointerId,start:world,center,target:svg.value}
  svg.value.focus({preventScroll:true});svg.value.setPointerCapture(e.pointerId)
  if(!inside)emit('navigate',center)
}
function move(e:PointerEvent){if(!gesture||e.pointerId!==gesture.pointer)return;const world=minimapWorldPoint(point(e),projection.value);emit('navigate',{x:gesture.center.x+world.x-gesture.start.x,y:gesture.center.y+world.y-gesture.start.y})}
function end(){const current=gesture;gesture=undefined;frozen.value=null;if(current?.target.hasPointerCapture(current.pointer))current.target.releasePointerCapture(current.pointer)}
function activate(e:MouseEvent){if(e.detail===0)emit('fit')}
function key(e:KeyboardEvent){
  if(['Enter',' '].includes(e.key)){e.preventDefault();emit('fit');return}
  const step=e.shiftKey?.5:.2,deltas:Record<string,CanvasPoint>={ArrowLeft:{x:-props.view.width*step,y:0},ArrowRight:{x:props.view.width*step,y:0},ArrowUp:{x:0,y:-props.view.height*step},ArrowDown:{x:0,y:props.view.height*step}}
  const delta=deltas[e.key];if(delta){e.preventDefault();emit('navigate',{x:props.view.x+props.view.width/2+delta.x,y:props.view.y+props.view.height/2+delta.y})}
}
onMounted(()=>{observer=new ResizeObserver(entries=>{const bounds=entries[0]?.contentRect;if(bounds&&bounds.width>0&&bounds.height>0){size.width=bounds.width;size.height=bounds.height}});if(svg.value)observer.observe(svg.value);window.addEventListener('blur',end)})
onBeforeUnmount(()=>{end();observer?.disconnect();window.removeEventListener('blur',end)})
</script>
<template><div class="canvas-minimap live-minimap"><svg ref="svg" :viewBox="`0 0 ${size.width} ${size.height}`" preserveAspectRatio="none" role="button" tabindex="0" aria-label="画布缩略导航：点击定位，拖动可见区域，方向键平移，Enter 适配全部论文" title="点击定位 · 拖动可见区域 · 方向键平移 · Enter 适配全部" @pointerdown.stop.prevent="begin" @pointermove.stop.prevent="move" @pointerup.stop="end" @pointercancel.stop="end" @lostpointercapture.stop="end" @click.stop="activate" @keydown="key"><rect v-for="node in nodes" :key="node.id" :data-minimap-paper-id="node.id" :class="[node.category,{'minimap-node-selected':node.selected}]" class="minimap-node" :x="node.rect.x" :y="node.rect.y" :width="node.rect.width" :height="node.rect.height" rx="1" aria-hidden="true"/><rect class="minimap-viewport" :x="visible.x" :y="visible.y" :width="visible.width" :height="visible.height" rx="2" aria-hidden="true"/></svg></div></template>
