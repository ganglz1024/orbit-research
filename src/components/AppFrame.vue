<script setup lang="ts">
import Sidebar from './AppSidebar.vue'
import Icon from './AppIcon.vue'
import { useResearchStore } from '../stores/research'
defineProps<{title:string;subtitle:string;eyebrow?:string}>()
const store=useResearchStore()
</script>
<template><div class="app-shell"><Sidebar/><main id="main-content" class="standard-main"><header class="standard-topbar"><button class="icon-button mobile-menu-button" aria-label="打开导航" @click="store.mobileMenu=true"><Icon name="Menu"/></button><span>我的空间<span class="breadcrumb-slash">/</span>{{title}}</span></header><div class="standard-page-content"><div class="standard-page-heading"><span v-if="eyebrow" class="eyebrow">{{eyebrow}}</span><h1>{{title}}</h1><p>{{subtitle}}</p></div><slot/></div></main></div></template>
