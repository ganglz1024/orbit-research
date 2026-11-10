/** Scroll progress uses a normalized 0..1 timeline; distances are measured at refresh. */
export const HOME_MOTION = {
  desktopMinWidth: 1024,
  desktopMinHeight: 720,
  scrollViewports: 3.5,
  scrubSeconds: 0.8,
  anticipatePin: 1,
  titleSecondarySize: 24,
  cardOffset: 52,
  cardScale: 0.96,
  // Normalized timeline units: a shorter reveal leaves more room between card starts.
  cardRevealDuration: 0.11,
  readerOffset: 20,
  contentFadeSeconds: 0.2,
  revealOffset: 24,
  revealSeconds: 0.6,
  stages: {
    still: 0,
    title: 0.08,
    titleSettled: 0.22,
    question: 0.18,
    papers: 0.24,
    connections: 0.56,
    reading: 0.70,
    // Keep the completed layout still for the final 18% before releasing the pin.
    settled: 0.82,
    end: 1,
  },
} as const

export const HOME_STEPS = [
  { title: '提出问题', caption: '一个好问题，是所有连接的起点。', progress: 0 },
  { title: '发现论文', caption: '让零散的论文，逐渐找到彼此。', progress: HOME_MOTION.stages.connections },
  { title: '连接线索', caption: '沿着一条连接，看见另一种思路。', progress: HOME_MOTION.stages.reading },
  { title: '深入理解', caption: '选一篇论文，从这里开始深入。', progress: (HOME_MOTION.stages.settled + HOME_MOTION.stages.end) / 2 },
] as const

/** A logical canvas, fitted to the real available space rather than viewport-specific offsets. */
export const HOME_CANVAS = {
  width: 900,
  height: 510,
  cardWidth: 196,
  cardHeight: 210,
  readerGap: 30,
  papers: [
    { id: 'survey', x: 44, y: 24 },
    { id: 'rag', x: 328, y: 18 },
    { id: 'self-rag', x: 630, y: 28 },
    { id: 'crag', x: 60, y: 250 },
    { id: 'lost-middle', x: 350, y: 238 },
    { id: 'graph-rag', x: 648, y: 244 },
  ],
  // Decorative links belong to this landing scene, never to a user's research graph.
  connections: [
    ['survey', 'rag'],
    ['rag', 'self-rag'],
    ['rag', 'crag'],
    ['rag', 'lost-middle'],
    ['rag', 'graph-rag'],
    ['crag', 'lost-middle'],
    ['lost-middle', 'graph-rag'],
  ],
} as const

export function cardRevealStart(index: number) {
  const available = HOME_MOTION.stages.connections - HOME_MOTION.stages.papers - HOME_MOTION.cardRevealDuration
  return HOME_MOTION.stages.papers + index * available / (HOME_CANVAS.papers.length - 1)
}
