import type {Session} from '../types'

/** Remove live references, keeping historical messages and saved research outputs. */
export function pruneSessionPapers(session:Session,ids:Set<string>):Session{
  return {...session,paperIds:session.paperIds.filter(id=>!ids.has(id)),selectedIds:session.selectedIds.filter(id=>!ids.has(id)),edges:session.edges?.filter(([a,b])=>!ids.has(a)&&!ids.has(b)),relationDetails:session.relationDetails?.filter(e=>!ids.has(e.from)&&!ids.has(e.to))}
}
