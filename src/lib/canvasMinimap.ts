export interface CanvasRect {x:number;y:number;width:number;height:number}
export interface CanvasPoint {x:number;y:number}
export interface MinimapProjection {scale:number;left:number;top:number;bounds:CanvasRect}
export function canvasVisibleRect(offset:CanvasPoint,scale:number,width:number,height:number):CanvasRect{
  const zoom=Number.isFinite(scale)&&scale>0?scale:1
  return{x:-offset.x/zoom,y:-offset.y/zoom,width:Math.max(1,width)/zoom,height:Math.max(1,height)/zoom}
}
export function minimapProjection(nodes:CanvasRect[],view:CanvasRect,width:number,height:number,padding=6):MinimapProjection{
  const valid=[...nodes,view].filter(r=>[r.x,r.y,r.width,r.height].every(Number.isFinite)&&r.width>=0&&r.height>=0)
  if(!valid.length)valid.push({x:0,y:0,width:1,height:1})
  const x=Math.min(...valid.map(r=>r.x)),y=Math.min(...valid.map(r=>r.y))
  const spanX=Math.max(1,Math.max(...valid.map(r=>r.x+r.width))-x),spanY=Math.max(1,Math.max(...valid.map(r=>r.y+r.height))-y)
  const scale=Math.max(.000001,Math.min(Math.max(1,width-2*padding)/spanX,Math.max(1,height-2*padding)/spanY))
  return{scale,left:(width-spanX*scale)/2,top:(height-spanY*scale)/2,bounds:{x,y,width:spanX,height:spanY}}
}
export function projectMinimapRect(rect:CanvasRect,projection:MinimapProjection):CanvasRect{
  return{x:projection.left+(rect.x-projection.bounds.x)*projection.scale,y:projection.top+(rect.y-projection.bounds.y)*projection.scale,width:rect.width*projection.scale,height:rect.height*projection.scale}
}
export function minimapWorldPoint(point:CanvasPoint,projection:MinimapProjection):CanvasPoint{
  return{x:projection.bounds.x+(point.x-projection.left)/projection.scale,y:projection.bounds.y+(point.y-projection.top)/projection.scale}
}
