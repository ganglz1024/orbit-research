<script setup lang="ts">
import { ref } from 'vue'
import { useResearchStore } from '../stores/research'
import { useHomeReveals } from '../composables/useHomeReveals'
import Brand from '../components/BrandMark.vue'
import Icon from '../components/AppIcon.vue'
import HomeScrollScene from '../components/HomeScrollScene.vue'
import HomeResearchSteps from '../components/HomeResearchSteps.vue'
import HomeCopilotTasks from '../components/HomeCopilotTasks.vue'

const store = useResearchStore()
const page = ref<HTMLElement>()
const scene = ref<InstanceType<typeof HomeScrollScene>>()
useHomeReveals(page)
</script>

<template>
  <div ref="page" class="landing-page landing-page--scroll">
    <main id="main-content">
      <HomeScrollScene ref="scene" />
      <HomeResearchSteps @seek="scene?.seekStep($event)" />
      <HomeCopilotTasks/>
      <section id="workflow" class="workflow-section">
        <div class="section-heading" data-home-reveal="heading">
          <span class="eyebrow">A SPACE FOR YOUR NEXT DISCOVERY</span>
          <h2>不止找到论文，<br>更接近自己的答案。</h2>
          <p>从发现到理解，从理解到表达。让研究中的每一步自然衔接。</p>
        </div>
        <div class="workflow-grid">
          <article data-home-reveal="discover">
            <span class="feature-icon violet"><Icon name="Compass" :size="24" /></span>
            <span class="feature-number">01 / DISCOVER</span>
            <h3>把问题，变成线索。</h3>
            <p>从自然语言出发，探索论文之间的关系。展开一条连接，发现另一种视角。</p>
            <RouterLink to="/chat">探索论文图谱<Icon name="ArrowUpRight" :size="16" /></RouterLink>
          </article>
          <article data-home-reveal="understand">
            <span class="feature-icon teal"><Icon name="BookOpen" :size="24" /></span>
            <span class="feature-number">02 / UNDERSTAND</span>
            <h3>与论文，认真对话。</h3>
            <p>阅读、划线、留下自己的判断。选中不理解的段落，让 AI 帮你拆开理解。</p>
            <button @click="store.uploadOpen = true">上传并阅读论文<Icon name="ArrowUpRight" :size="16" /></button>
          </article>
          <article data-home-reveal="create">
            <span class="feature-icon amber"><Icon name="Presentation" :size="24" /></span>
            <span class="feature-number">03 / CREATE</span>
            <h3>让思考，有形可见。</h3>
            <p>把选定的材料整理成汇报。保留来源，逐页修改，把研究脉络讲清楚。</p>
            <RouterLink to="/artifacts">查看汇报空间<Icon name="ArrowUpRight" :size="16" /></RouterLink>
          </article>
        </div>
      </section>
      <section class="landing-closing" data-home-reveal="closing">
        <div class="closing-orbit" aria-hidden="true"></div>
        <Brand compact />
        <h2>下一个发现，从你的问题开始。</h2>
        <RouterLink class="primary-button" to="/chat">打开我的研究空间<Icon name="ArrowRight" :size="17" /></RouterLink>
        <span>无需配置，先体验完整的科研交互。</span>
      </section>
    </main>
    <footer class="landing-footer"><Brand /><span>保持好奇，循迹而行。</span><span>© 2026 Orbit</span></footer>
  </div>
</template>

<style scoped>
/* The document is the only scrolling container; pin spacing owns the narrative length. */
.landing-page--scroll { overflow: visible; }
.workflow-section { margin-top: 96px; }
@media (max-width: 700px) {
  .workflow-section { margin-top: 64px; }
  .workflow-section :deep(.section-heading h2) { font-size: 28px; }
}
</style>
