import type {ReadingRequest} from '../lib/readerContext'
import {defineStore} from 'pinia'
import {ref} from 'vue'
import type {ComposerReference} from '../lib/composerContext'
export interface ComposerDraft {text:string;references:ComposerReference[];skillId:string;ignoreCanvas:boolean;pending?:{kind?:'analysis';runId:string;text:string;references:ComposerReference[];skillId:string;reading?:ReadingRequest};failed?:{text:string;references:ComposerReference[];skillId:string;reading?:ReadingRequest}}
// Only UI drafts live here. Messages and task state remain in the original stores.
export const useComposerStore=defineStore('composer',()=>{
  const drafts=ref<Record<string,ComposerDraft>>({})
  function forSpace(owner:string,space:string){const key=owner+':'+space;if(!drafts.value[key])drafts.value[key]={text:'',references:[],skillId:'',ignoreCanvas:false};return drafts.value[key]!}
  return{drafts,forSpace}
})
