// Event recorder for the adversarial test page (see docs/design/testing.md).
//
// It plays the part of a web page spying on the user: every keyboard and input event the
// browser hands to a text box is kept with its timestamp, and every `input` event also keeps a
// snapshot of what the box holds. Nothing here touches the network; a session only leaves the
// page when the tester downloads it as JSON.
//
// Plain script, no build step: the browser loads it with a classic <script> tag (ES modules are
// blocked on file:// pages), and Node loads it with require() for `node --test`.
;(function (root, factory) {
  const api = factory()
  if (typeof module === 'object' && module.exports) {
    module.exports = api
  } else {
    root.AdversaryRecorder = api
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict'

  // Bumped when the session layout changes, so later matchers can refuse old files.
  const SCHEMA = 1
  const KIND = 'chamboard-adversary-session'

  // The events a tracking script can see while someone types. keyup and the composition events
  // go beyond the three docs/design/testing.md names: keyup gives dwell time, and composition shows
  // whether a keyboard built the word on screen first (most phone keyboards do) or committed
  // it all at once.
  const EVENT_TYPES = [
    'keydown',
    'keyup',
    'beforeinput',
    'input',
    'compositionstart',
    'compositionupdate',
    'compositionend',
  ]

  function pick(event, name) {
    const value = event[name]
    return value === undefined ? null : value
  }

  // Keeps one event as plain data. `field` is the text box (or anything with value and
  // selectionStart/selectionEnd); its contents are kept only after `input`, when they changed.
  function snapshot(event, field, origin) {
    const entry = {
      type: event.type,
      t: Math.round((event.timeStamp - origin) * 1000) / 1000,
    }
    if (event.type === 'keydown' || event.type === 'keyup') {
      entry.key = pick(event, 'key')
      entry.code = pick(event, 'code')
      entry.keyCode = pick(event, 'keyCode')
      entry.repeat = Boolean(event.repeat)
      entry.isComposing = Boolean(event.isComposing)
    } else if (event.type === 'beforeinput' || event.type === 'input') {
      entry.inputType = pick(event, 'inputType')
      entry.data = pick(event, 'data')
      entry.isComposing = Boolean(event.isComposing)
    } else {
      entry.data = pick(event, 'data')
    }
    if (event.type === 'input' && field) {
      entry.value = String(field.value)
      entry.selStart = pick(field, 'selectionStart')
      entry.selEnd = pick(field, 'selectionEnd')
    }
    return entry
  }

  // `options.label` names the session ("with ChamBoard", "plain Gboard"...).
  // `options.startedAt` is a wall-clock string, injected so tests stay deterministic.
  function createRecorder(options) {
    const opts = options || {}
    let events = []
    let origin = null

    return {
      record(event, field) {
        if (EVENT_TYPES.indexOf(event.type) < 0) return null
        if (origin === null) origin = event.timeStamp
        const entry = snapshot(event, field, origin)
        events.push(entry)
        return entry
      },
      clear() {
        events = []
        origin = null
      },
      get count() {
        return events.length
      },
      session() {
        return {
          kind: KIND,
          schema: SCHEMA,
          label: opts.label || '',
          startedAt: opts.startedAt || null,
          userAgent: opts.userAgent || null,
          events: events.map((e) => Object.assign({}, e)),
        }
      },
    }
  }

  // Throws if `data` is not a session this page wrote; returns it otherwise.
  function validateSession(data) {
    if (!data || data.kind !== KIND) throw new Error('not a ChamBoard adversary session')
    if (data.schema !== SCHEMA) throw new Error('unsupported session schema ' + data.schema)
    if (!Array.isArray(data.events)) throw new Error('session has no events list')
    return data
  }

  // A few headline numbers for the page: how long, how many keys, and how text arrived.
  function summarize(session) {
    const events = session.events
    const count = (type) => events.filter((e) => e.type === type).length
    const inputs = events.filter((e) => e.type === 'input')
    const last = inputs.length ? inputs[inputs.length - 1] : null
    const multiChar = inputs.filter(
      (e) => e.inputType === 'insertText' && typeof e.data === 'string' && e.data.length > 1,
    ).length
    return {
      events: events.length,
      durationMs: events.length ? events[events.length - 1].t - events[0].t : 0,
      keydowns: count('keydown'),
      inputs: inputs.length,
      compositions: count('compositionstart'),
      multiCharInserts: multiChar,
      keyCode229: events.filter((e) => e.type === 'keydown' && e.keyCode === 229).length,
      finalLength: last && typeof last.value === 'string' ? last.value.length : 0,
    }
  }

  return { SCHEMA, KIND, EVENT_TYPES, createRecorder, validateSession, summarize }
})
