// Recorder tests (see docs/design/testing.md). Run: node --test adversary/
'use strict'
const test = require('node:test')
const assert = require('node:assert/strict')
const R = require('../recorder.js')

function field(value, cursor) {
  return { value, selectionStart: cursor, selectionEnd: cursor }
}

test('records key, input and composition events with times relative to the first one', () => {
  const rec = R.createRecorder({ label: 'plain', startedAt: '2026-10-13T00:00:00.000Z' })
  rec.record({ type: 'keydown', timeStamp: 1000.5, key: 'h', code: 'KeyH', keyCode: 72 })
  rec.record({ type: 'beforeinput', timeStamp: 1001, inputType: 'insertText', data: 'h' })
  rec.record({ type: 'input', timeStamp: 1001.25, inputType: 'insertText', data: 'h' }, field('h', 1))
  rec.record({ type: 'keyup', timeStamp: 1080.5, key: 'h', code: 'KeyH', keyCode: 72 })
  rec.record({ type: 'compositionstart', timeStamp: 1100.5, data: '' })

  const s = rec.session()
  assert.equal(s.kind, R.KIND)
  assert.equal(s.schema, R.SCHEMA)
  assert.equal(s.label, 'plain')
  assert.deepEqual(
    s.events.map((e) => [e.type, e.t]),
    [
      ['keydown', 0],
      ['beforeinput', 0.5],
      ['input', 0.75],
      ['keyup', 80],
      ['compositionstart', 100],
    ],
  )
  assert.equal(s.events[0].keyCode, 72)
  assert.equal(s.events[2].value, 'h')
  assert.equal(s.events[2].selStart, 1)
})

test('takes a snapshot of the box only on input events', () => {
  const rec = R.createRecorder()
  rec.record({ type: 'keydown', timeStamp: 0, key: 'a', keyCode: 65 }, field('', 0))
  rec.record({ type: 'beforeinput', timeStamp: 1, inputType: 'insertText', data: 'a' }, field('', 0))
  rec.record({ type: 'input', timeStamp: 2, inputType: 'insertText', data: 'a' }, field('a', 1))
  const [down, before, input] = rec.session().events
  assert.equal('value' in down, false)
  assert.equal('value' in before, false)
  assert.equal(input.value, 'a')
})

test('ignores events a tracker would not use and can be cleared', () => {
  const rec = R.createRecorder()
  assert.equal(rec.record({ type: 'focus', timeStamp: 5 }), null)
  rec.record({ type: 'keydown', timeStamp: 10, key: 'x', keyCode: 88 })
  assert.equal(rec.count, 1)
  rec.clear()
  assert.equal(rec.count, 0)
  rec.record({ type: 'keydown', timeStamp: 50, key: 'y', keyCode: 89 })
  assert.equal(rec.session().events[0].t, 0)
})

test('session() returns a copy the caller cannot use to change the recording', () => {
  const rec = R.createRecorder()
  rec.record({ type: 'keydown', timeStamp: 0, key: 'a', keyCode: 65 })
  rec.session().events[0].key = 'tampered'
  assert.equal(rec.session().events[0].key, 'a')
})

test('a session survives a JSON round trip and validates', () => {
  const rec = R.createRecorder({ label: 'round trip' })
  rec.record({ type: 'input', timeStamp: 3, inputType: 'insertText', data: 'hi' }, field('hi', 2))
  const back = R.validateSession(JSON.parse(JSON.stringify(rec.session())))
  assert.equal(back.events[0].value, 'hi')
  assert.throws(() => R.validateSession({ kind: 'other' }), /not a ChamBoard/)
  assert.throws(() => R.validateSession({ kind: R.KIND, schema: 99, events: [] }), /schema/)
})

test('summary counts the signs of a keyboard that commits whole words', () => {
  const rec = R.createRecorder()
  // What a soft keyboard committing a word looks like: keyCode 229, one multi-character insert.
  rec.record({ type: 'keydown', timeStamp: 0, key: 'Unidentified', keyCode: 229 })
  rec.record({ type: 'input', timeStamp: 1, inputType: 'insertText', data: 'hello' }, field('hello', 5))
  rec.record({ type: 'keydown', timeStamp: 200, key: 'Unidentified', keyCode: 229 })
  rec.record({ type: 'input', timeStamp: 201, inputType: 'insertText', data: ' ' }, field('hello ', 6))
  const s = R.summarize(rec.session())
  assert.equal(s.keydowns, 2)
  assert.equal(s.keyCode229, 2)
  assert.equal(s.multiCharInserts, 1)
  assert.equal(s.finalLength, 6)
  assert.equal(s.durationMs, 201)
})
