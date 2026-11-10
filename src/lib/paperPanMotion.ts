import { PAPER_EXPLORER as SETTINGS } from '../config/paperExplorer'

const clamp = (value: number, limit: number) => Math.max(-limit, Math.min(limit, value))
const within = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value))

/** DOM-independent drag/inertia state. Coordinates stay intact when scrolling leaves the scene. */
export class PaperPanMotion {
  x = 0
  y = 0
  private vx = 0
  private vy = 0
  private gesture?: { x: number; y: number; panX: number; panY: number; time: number; scale: number }
  private moved = false
  private vertical = { min: -Infinity, max: Infinity }

  /** The visible viewport may have different amounts of room above and below the cards. */
  setVerticalBounds(min: number, max: number) {
    this.vertical = { min: Math.min(0, min), max: Math.max(0, max) }
    const next = this.restingY(this.y)
    if (next !== this.y) { this.cancel(); this.y = next }
  }

  begin(x: number, y: number, time: number, scale: number) {
    this.cancel()
    this.gesture = { x, y, panX: this.x, panY: this.y, time, scale: Math.max(0.1, scale) }
  }

  move(x: number, y: number, time: number) {
    const gesture = this.gesture
    if (!gesture || (!this.moved && Math.hypot(x - gesture.x, y - gesture.y) < SETTINGS.dragThreshold)) return false
    const ratio = SETTINGS.sensitivity / gesture.scale
    const nextX = this.resist(gesture.panX + (x - gesture.x) * ratio, SETTINGS.maxPanX)
    const nextY = this.resistY(gesture.panY + (y - gesture.y) * ratio)
    const dt = Math.max(8, Math.min(64, time - gesture.time))
    this.vx = clamp(this.vx * 0.35 + (nextX - this.x) / dt * 0.65, SETTINGS.maxVelocity)
    this.vy = clamp(this.vy * 0.35 + (nextY - this.y) / dt * 0.65, SETTINGS.maxVelocity)
    this.x = nextX; this.y = nextY
    gesture.time = time
    this.moved = true
    return true
  }

  end(time: number) {
    if (this.gesture && time - this.gesture.time > 80) this.vx = this.vy = 0
    const result = this.moved
    this.gesture = undefined
    this.moved = false
    return result
  }

  cancel() { this.gesture = undefined; this.moved = false; this.vx = this.vy = 0 }

  step(elapsedMs: number) {
    const dt = Math.max(0, Math.min(32, elapsedMs))
    const damping = Math.exp(-dt / SETTINGS.inertiaDecayMs)
    // Integrate exponential decay exactly so 60 Hz and 120 Hz feel alike.
    this.x += this.vx * SETTINGS.inertiaDecayMs * (1 - damping)
    this.y += this.vy * SETTINGS.inertiaDecayMs * (1 - damping)
    this.y = within(this.y, this.vertical.min, this.vertical.max)
    this.vx *= damping; this.vy *= damping
    for (const axis of ['x', 'y'] as const) {
      const edge = axis === 'x' ? clamp(this.x, SETTINGS.maxPanX) : this.restingY(this.y)
      if (this[axis] !== edge) {
        this[axis] += (edge - this[axis]) * (1 - Math.exp(-dt / 65))
        if (axis === 'x') this.vx *= 0.5
        else this.vy *= 0.5
      }
    }
    const outside = Math.abs(this.x - clamp(this.x, SETTINGS.maxPanX)) + Math.abs(this.y - this.restingY(this.y))
    if (Math.abs(this.vx) + Math.abs(this.vy) < SETTINGS.stopVelocity && outside < 0.25) {
      this.x = clamp(this.x, SETTINGS.maxPanX); this.y = this.restingY(this.y)
      this.cancel()
      return false
    }
    return true
  }

  nudge(dx: number, dy: number) {
    this.cancel()
    this.x = clamp(this.x + dx, SETTINGS.maxPanX)
    this.y = this.restingY(this.y + dy)
  }

  reset() { this.cancel(); this.x = this.y = 0 }

  private restingY(value: number) {
    return within(value, Math.max(-SETTINGS.maxPanY, this.vertical.min), Math.min(SETTINGS.maxPanY, this.vertical.max))
  }

  private resistY(value: number) {
    const edge = this.restingY(value)
    // Only show elastic overshoot where there is real visual space for it.
    return within(edge + clamp((value - edge) * SETTINGS.boundaryResistance, SETTINGS.maxOvershoot), this.vertical.min, this.vertical.max)
  }

  private resist(value: number, limit: number) {
    return clamp(value, limit) + clamp((value - clamp(value, limit)) * SETTINGS.boundaryResistance, SETTINGS.maxOvershoot)
  }
}
