import { computed,onMounted,onBeforeUnmount,ref } from 'vue'
import { readLocal,writeLocal } from '../lib/storage'

export function useWorkspaceHeight(){
  const headerRegion=ref<HTMLElement>(),contentRegion=ref<HTMLElement>(),workspaceRegion=ref<HTMLElement>()
  const height=ref<number>(),maxHeight=ref(320),resizing=ref(false),minHeight=64
  let naturalHeight=170,gesture:{y:number;height:number;pointer:number;target:HTMLElement}|undefined
  const headerStyle=computed(()=>height.value===undefined?{}:{height:height.value+'px'})
  function bounds(){const header=headerRegion.value,content=contentRegion.value;if(header&&content)maxHeight.value=Math.max(minHeight,Math.min(320,header.offsetHeight+content.clientHeight-220))}
  function assign(value:number){height.value=Math.round(Math.max(minHeight,Math.min(maxHeight.value,value)))}
  function save(){try{writeLocal('orbit:workspace-header-height',height.value)}catch{/* Resizing remains usable when storage is unavailable. */}}
  function adjust(delta:number){bounds();assign((height.value||naturalHeight)+delta);save()}
  function reset(){bounds();assign(naturalHeight);save()}
  function begin(event:PointerEvent){if(event.button!==0||!event.isPrimary)return;bounds();gesture={y:event.clientY,height:headerRegion.value?.offsetHeight||naturalHeight,pointer:event.pointerId,target:event.currentTarget as HTMLElement};gesture.target.setPointerCapture(event.pointerId);resizing.value=true;event.preventDefault()}
  function move(event:PointerEvent){if(!gesture||gesture.pointer!==event.pointerId)return;assign(gesture.height+event.clientY-gesture.y);event.preventDefault()}
  function end(){const current=gesture;gesture=undefined;resizing.value=false;if(current?.target.hasPointerCapture(current.pointer))current.target.releasePointerCapture(current.pointer);save()}
  function keys(event:KeyboardEvent){if(['ArrowUp','ArrowDown','Home','End','Enter'].includes(event.key)){event.preventDefault();if(event.key==='Enter')reset();else if(event.key==='Home'){assign(minHeight);save()}else if(event.key==='End'){bounds();assign(maxHeight.value);save()}else adjust(event.key==='ArrowUp'?-20:20)}}
  function resized(){bounds();if(height.value!==undefined)assign(height.value)}
  onMounted(()=>{naturalHeight=headerRegion.value?.offsetHeight||170;bounds();const saved=readLocal<number|null>('orbit:workspace-header-height',null);if(typeof saved==='number'&&Number.isFinite(saved))assign(saved);else assign(naturalHeight);window.addEventListener('resize',resized);window.addEventListener('blur',end)})
  onBeforeUnmount(()=>{end();window.removeEventListener('resize',resized);window.removeEventListener('blur',end)})
  return {headerRegion,contentRegion,workspaceRegion,height,maxHeight,minHeight,headerStyle,resizing,adjust,reset,begin,move,end,keys}
}
