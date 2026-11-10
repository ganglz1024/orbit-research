export type SystemCommandId = 'clear'|'compact'|'status'|'model'|'reasoning'|'history'|'help'|'export'
export const systemCommands: {id:SystemCommandId; name:string; description:string; category:'会话管理'|'模型设置'; available:boolean}[] = [
  {id:'clear',name:'/clear',description:'开始新对话，保留历史',category:'会话管理',available:true},
  {id:'compact',name:'/compact',description:'生成上下文摘要，确认后应用',category:'会话管理',available:true},
  {id:'status',name:'/status',description:'查看当前会话与来源状态',category:'会话管理',available:true},
  {id:'history',name:'/history',description:'查看历史对话与分析',category:'会话管理',available:true},
  {id:'export',name:'/export',description:'导出当前对话为 Markdown',category:'会话管理',available:true},
  {id:'help',name:'/help',description:'查看命令说明',category:'会话管理',available:true},
  {id:'model',name:'/model',description:'查看当前模型与配置',category:'模型设置',available:true},
  {id:'reasoning',name:'/reasoning',description:'打开思考程度设置',category:'模型设置',available:true},
]
export function findSystemCommand(input:string){return systemCommands.find(command=>input.trim().toLowerCase()==='/'+command.id)}
