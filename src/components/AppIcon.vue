<script setup lang="ts">
import { computed } from 'vue'
import { UserRound } from '@lucide/vue'
import { ArrowUpRight, ArrowUp, ArrowRight, ArrowLeft, Plus, Minus, Search, Settings2, Upload, FileText, Network, Sparkles, Presentation, ChevronUp, ChevronDown, ChevronLeft, ChevronRight, X, Check, MoreHorizontal, Maximize2, Move, GripVertical, PanelRightClose, PanelRightOpen, LayoutGrid, List, Compass, BookOpen, Download, Command, CircleHelp, Layers, Link2, Highlighter, Underline, MessageSquare, RotateCw, Focus, SlidersHorizontal, Menu, Square, ExternalLink, Copy, CheckCheck, LoaderCircle, Trash2, MousePointer2, Pencil, FileUp, Bell, ShieldCheck, Circle, Zap } from '@lucide/vue'
const props=withDefaults(defineProps<{name:string;size?:number}>(),{size:18})
const icons:Record<string,unknown>={ArrowUpRight,ArrowUp,ArrowRight,ArrowLeft,Plus,Minus,Search,Settings2,Upload,FileText,Network,Sparkles,Presentation,ChevronUp,ChevronDown,ChevronLeft,ChevronRight,X,Check,MoreHorizontal,Maximize2,Move,GripVertical,PanelRightClose,PanelRightOpen,LayoutGrid,List,Compass,BookOpen,Download,Command,CircleHelp,Layers,Link2,Highlighter,Underline,MessageSquare,RotateCw,Focus,SlidersHorizontal,Menu,Square,ExternalLink,Copy,CheckCheck,LoaderCircle,Trash2,MousePointer2,Pencil,FileUp,Bell,ShieldCheck,Circle,Zap}
const icon=computed(()=>props.name==='UserRound'?UserRound:icons[props.name]||Circle)
</script>
<template><component :is="icon" :size="size" :stroke-width="1.65" aria-hidden="true" /></template>

