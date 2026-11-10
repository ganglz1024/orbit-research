export function submitsPrompt(event: Pick<KeyboardEvent, 'key' | 'shiftKey' | 'ctrlKey' | 'metaKey' | 'altKey' | 'isComposing' | 'keyCode'>, composing: boolean) {
  return event.key === 'Enter' && !event.shiftKey && !event.ctrlKey && !event.metaKey && !event.altKey
    && !event.isComposing && event.keyCode !== 229 && !composing
}

/** Preserve edits made while an accepted request was being processed. */
export function promptAfterSend(current: string, submitted: string, accepted: boolean) {
  return accepted && current === submitted ? '' : current
}
