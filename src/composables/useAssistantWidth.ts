import {computed,onBeforeUnmount,onMounted,ref,nextTick} from 'vue'
import {readLocal,writeLocal} from '../lib/storage'
import {ASSISTANT_WIDTH,assistantWidthLimits,clampAssistantWidth} from '../lib/workspaceLayout'

export function useAssistantWidth(){
  const splitRegion=ref<HTMLElement>(),width=ref<number>(),total=ref(1200),resizing=ref(false)
  const limits=computed(()=>assistantWidthLimits(total.value))
  const style=computed(()=>width.value===undefined?{}:{'--assistant-width':width.value+'px'})
  let natural=344,preferred:number|undefined,observer:ResizeObserver|undefined,gesture:{x:number;width:number;pointer:number;target:HTMLElement}|undefined
  function measure(){total.value=splitRegion.value?.clientWidth||innerWidth;if(width.value!==undefined)width.value=clampAssistantWidth(preferred??width.value,total.value)}
  function save(){try{writeLocal('orbit:assistant-width',preferred??width.value)}catch{/* Keep resizing usable when browser storage is unavailable. */}}
  function set(value:number){measure();width.value=clampAssistantWidth(value,total.value);preferred=width.value}
  function adjust(delta:number){set((width.value||natural)+delta);save()}
  function reset(){set(natural);save()}
  function begin(e:PointerEvent){if(e.button!==0||!e.isPrimary)return;measure();gesture={x:e.clientX,width:width.value||natural,pointer:e.pointerId,target:e.currentTarget as HTMLElement};gesture.target.setPointerCapture(e.pointerId);resizing.value=true;e.preventDefault()}
  function move(e:PointerEvent){if(!gesture||e.pointerId!==gesture.pointer)return;set(gesture.width+gesture.x-e.clientX);e.preventDefault()}
  function end(){const current=gesture;gesture=undefined;resizing.value=false;if(current?.target.hasPointerCapture(current.pointer))current.target.releasePointerCapture(current.pointer);save()}
  function keys(e:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End','Enter'].includes(e.key))return;e.preventDefault();if(e.key==='Enter')reset();else if(e.key==='Home'){set(limits.value.min);save()}else if(e.key==='End'){set(limits.value.max);save()}else adjust(e.key==='ArrowLeft'?ASSISTANT_WIDTH.step:-ASSISTANT_WIDTH.step)}
  onMounted(async()=>{await nextTick();if(!splitRegion.value)return;natural=parseFloat(getComputedStyle(splitRegion.value).getPropertyValue('--assistant-width'))||344;measure();const saved=readLocal<number|null>('orbit:assistant-width',null);if(typeof saved==='number'&&Number.isFinite(saved)){preferred=saved;width.value=clampAssistantWidth(saved,total.value)}else set(natural);observer=new ResizeObserver(measure);observer.observe(splitRegion.value);window.addEventListener('blur',end)})
  onBeforeUnmount(()=>{end();observer?.disconnect();window.removeEventListener('blur',end)})
  return{splitRegion,width,style,limits,resizing,begin,move,end,keys,adjust,reset}
}
