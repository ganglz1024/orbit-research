export const ASSISTANT_WIDTH={min:300,max:720,canvasMin:520,step:24}
export function assistantWidthLimits(total:number){return {min:ASSISTANT_WIDTH.min,max:Math.max(ASSISTANT_WIDTH.min,Math.min(ASSISTANT_WIDTH.max,total-ASSISTANT_WIDTH.canvasMin))}}
export function clampAssistantWidth(value:number,total:number){const {min,max}=assistantWidthLimits(total);return Math.round(Math.min(max,Math.max(min,Number.isFinite(value)?value:min)))}
export function reorderWorkspace<T extends {id:string}>(items:T[],id:string,target:string,after=false):T[]{
  if(id===target||!items.some(s=>s.id===id)||!items.some(s=>s.id===target))return items
  const moved=items.find(s=>s.id===id)!,others=items.filter(s=>s.id!==id),index=others.findIndex(s=>s.id===target)
  return [...others.slice(0,index+(after?1:0)),moved,...others.slice(index+(after?1:0))]
}
