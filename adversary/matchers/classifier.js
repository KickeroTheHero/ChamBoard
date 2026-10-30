// "Human or ChamBoard?" classifier for the adversarial test page (see docs/design/testing.md).
//
// Plays a tracker that tries to tell whether text was typed live or replayed by ChamBoard.
// It adds up simple, explainable signals and says which ones fired. It knows what the keyboard
// does (see docs/design/engine.md, Timing): text is staged in a private draft and then committed
// to the page character by character (Typed), word by word (Glide) or all at once (Instant),
// without the on-screen word composition most phone keyboards use, and corrections are a
// backspace followed by the right letter. The engine's known limits already concede a tuned
// classifier may spot it; this page measures how easily.
;(function (root, factory) {
  const api = factory()
  if (typeof module === 'object' && module.exports) {
    module.exports = api
  } else {
    root.AdversaryClassifier = api
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict'

  // Need at least this many text changes before judging.
  const MIN_INPUTS = 10
  const CHAMBOARD_AT = 2
  const HUMAN_AT = -1

  function signals(session) {
    const ev = session.events
    const inputs = ev.filter((e) => e.type === 'input')
    const keydowns = ev.filter((e) => e.type === 'keydown')
    const compositions = ev.filter((e) => e.type === 'compositionstart').length
    const realKeys = keydowns.filter((e) => e.keyCode !== 229 && typeof e.key === 'string' && e.key.length === 1)
    const softKeys = keydowns.filter((e) => e.keyCode === 229).length
    const inserts = inputs.filter((e) => e.inputType === 'insertText')
    const multi = inserts.filter((e) => typeof e.data === 'string' && e.data.length > 1).length
    const deletes = inputs.filter((e) => typeof e.inputType === 'string' && e.inputType.startsWith('delete'))
    const atEnd = inputs.filter((e) => typeof e.value === 'string' && e.selStart === e.value.length).length

    // A run of two or more deletions in a row: people hold backspace or fix several letters;
    // the replayed typo is always one wrong letter, one backspace (TIM-8).
    let deleteRuns = 0
    for (let i = 1; i < inputs.length; i++) {
      const del = (e) => typeof e.inputType === 'string' && e.inputType.startsWith('delete')
      if (del(inputs[i]) && del(inputs[i - 1])) deleteRuns++
    }

    const out = []
    const add = (name, weight, why) => out.push({ name, weight, why })
    if (realKeys.length >= inputs.length / 2 && realKeys.length > 0) {
      add('physical-keys', -3, 'Real key names arrive with the text: someone is typing on a physical keyboard into the page.')
    }
    if (compositions > 0) {
      add('composition', -2, 'The keyboard built words on screen first (composition events), as most phone keyboards do.')
    } else if (softKeys > 0 && inputs.length >= MIN_INPUTS) {
      add('no-composition', 2, 'Phone-keyboard text with no on-screen word composition: text was committed directly.')
    }
    if (multi > 0 && compositions === 0) {
      add('whole-words', 2, multi + ' insert(s) of several characters at once without composition or paste (Glide or Instant playback).')
    }
    if (inputs.length >= 20 && atEnd === inputs.length) {
      add('append-only', 1, 'Every change happened at the end of the text; nothing was edited in the middle.')
    }
    if (deletes.length > 0 && deleteRuns === 0) {
      add('single-backspaces', 0.5, 'Every correction was exactly one backspace.')
    } else if (deleteRuns > 0) {
      add('backspace-runs', -1, deleteRuns + ' run(s) of several backspaces in a row, typical of a person fixing a word.')
    }
    return out
  }

  // { verdict: 'chamboard' | 'human' | 'unsure', score, reasons }
  function classify(session) {
    const inputs = session.events.filter((e) => e.type === 'input').length
    if (inputs < MIN_INPUTS) {
      return { verdict: 'unsure', score: 0, reasons: [{ name: 'too-short', weight: 0, why: 'Not enough typing to judge.' }] }
    }
    const reasons = signals(session)
    const score = reasons.reduce((sum, r) => sum + r.weight, 0)
    const verdict = score >= CHAMBOARD_AT ? 'chamboard' : score <= HUMAN_AT ? 'human' : 'unsure'
    return { verdict, score, reasons }
  }

  return { classify, signals, MIN_INPUTS }
})
