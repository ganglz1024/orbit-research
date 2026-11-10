/** Local, pre-rendered first pages. The UI never downloads the full PDFs. */
export const PAPER_COVERS: Record<string, string> = {
  survey: '/images/papers/01_rag_survey.webp',
  rag: '/images/papers/02_retrieval_augmented_generation.webp',
  'self-rag': '/images/papers/03_self_rag.webp',
  crag: '/images/papers/04_corrective_rag.webp',
  'lost-middle': '/images/papers/05_lost_in_the_middle.webp',
  'graph-rag': '/images/papers/06_graph_rag.webp',
}

export const PAPER_EXPLORER = {
  viewport: {
    horizontalBleed: 150,
    verticalBleed: 180,
    readerClearance: 18,
    stageGutter: 28,
    edgeFeather: 12,
    verticalFeather: 18,
    verticalClearance: 10,
    // Covers the largest Y response of the current perspective/depth/parallax layers.
    verticalPanResponse: 1.25,
    projectionGuard: 6,
  },
  perspective: 1100,
  selectedLift: 16,
  parallax: 0.12,
  sensitivity: 1,
  dragThreshold: 7,
  maxPanX: 110,
  maxPanY: 68,
  boundaryResistance: 0.22,
  maxOvershoot: 24,
  inertiaDecayMs: 160,
  maxVelocity: 1.8,
  stopVelocity: 0.012,
  keyboardStep: 48,
  layers: [
    { z: -44, rx: -2, ry: 5, rz: -3 },
    { z: 56, rx: 2, ry: -4, rz: 1 },
    { z: -26, rx: -2, ry: -5, rz: 3 },
    { z: 12, rx: 2, ry: 4, rz: -2 },
    { z: -48, rx: -2, ry: 4, rz: 2 },
    { z: 4, rx: 1, ry: -4, rz: -2 },
  ],
} as const
