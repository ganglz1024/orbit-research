import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('./views/HomeView.vue') },
    { path: '/chat/:id?', alias: ['/workspace/:id?', '/graphs/:id?'], component: () => import('./views/WorkspaceView.vue') },
    { path: '/documents/:id/read', component: () => import('./views/ReaderView.vue') },
    { path: '/skills', component: () => import('./views/SkillsView.vue') },
    { path: '/artifacts/:id?', component: () => import('./views/ArtifactsView.vue') },
    { path: '/settings', component: () => import('./views/SettingsView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
