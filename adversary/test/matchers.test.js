// Matcher tests on fixture sessions (see docs/design/testing.md). Run: node --test 'adversary/**/*.test.js'
'use strict'
const test = require('node:test')
const assert = require('node:assert/strict')
const K = require('../matchers/keystroke.js')
const S = require('../matchers/stylometry.js')
const C = require('../matchers/classifier.js')
const F = require('./fixtures.js')

// Two typists with their own speed and their own fast letter pairs.
const alice = (seed) => F.hardware(F.SAMPLE, { seed, median: 140, fast: ['th', 'he', 'in'], label: 'alice' })
const bob = (seed) => F.hardware(F.SAMPLE, { seed, median: 260, sigma: 0.5, fast: ['an', 'er'], label: 'bob' })

test('keystroke: one stroke per text change, deletions included', () => {
  const s = F.hardware('abc', { typoRate: 0 })
  assert.deepEqual(K.strokes(s).map((x) => x.ch), ['a', 'b', 'c'])
  const typo = F.chamboard('hello world', { typoRate: 1, seed: 9 })
  assert.ok(K.strokes(typo).some((x) => x.ch === '⌫'))
})

test('keystroke: profile captures speed and fast digraphs', () => {
  const p = K.profile(alice(11))
  const q = K.profile(bob(12))
  assert.ok(p.median < q.median, `alice ${p.median} should be faster than bob ${q.median}`)
  assert.ok(Math.abs(p.histogram.reduce((a, b) => a + b, 0) - 1) < 1e-9)
  assert.ok('th' in p.digraphs && p.digraphs.th < p.median, 'alice types "th" faster than her median')
})

test('keystroke: identical sessions have zero distance', () => {
  const p = K.profile(alice(11))
  assert.equal(K.distance(p, p), 0)
})

test('keystroke: recognises a typist from a new session (rhythm leaks without ChamBoard)', () => {
  const enrolled = { alice: alice(21), bob: bob(22) }
  assert.equal(K.identify(alice(31), enrolled).best, 'alice')
  assert.equal(K.identify(bob(32), enrolled).best, 'bob')
  assert.ok(K.identify(alice(31), enrolled).margin > 0.2)
})

test('keystroke: ChamBoard replays with the mask rhythm, not the typist', () => {
  // Same mask (same persona) for both people: the matcher can only see the persona.
  const enrolled = { alice: alice(21), bob: bob(22) }
  const viaMaskA = F.chamboard(F.SAMPLE, { seed: 41, median: 200 })
  const viaMaskB = F.chamboard(F.SAMPLE, { seed: 42, median: 200 })
  const pa = K.profile(viaMaskA)
  const pb = K.profile(viaMaskB)
  // Two people behind the same mask look alike: closer to each other than alice is to bob.
  assert.ok(K.distance(pa, pb) < K.distance(K.profile(alice(21)), K.profile(bob(22))))
  assert.ok(!('th' in pa.digraphs) || pa.digraphs.th > pa.median / 2, 'no trace of alice\'s fast "th"')
  assert.ok(Array.isArray(K.identify(viaMaskA, enrolled).ranked))
})

test('stylometry: identical texts have zero distance', () => {
  const f = S.fingerprint(F.FORMAL_BRIT)
  assert.equal(S.distance(f, f), 0)
})

test('stylometry: picks up the habits StyleFingerprint measures', () => {
  const brit = S.fingerprint(F.FORMAL_BRIT)
  const yank = S.fingerprint(F.CASUAL_AMERICAN)
  assert.ok(brit['spelling:british'] > 0 && brit['spelling:american'] === 0)
  assert.ok(yank['spelling:american'] > 0 && yank['spelling:british'] === 0)
  assert.ok(yank['case:lower-i'] > 0 && brit['case:lower-i'] === 0)
  assert.ok(yank['case:lower-start'] > brit['case:lower-start'])
  assert.ok(yank['contraction:rate'] > brit['contraction:rate'])
  assert.ok(brit['punct:runs'] > 0, '"!!!" is a punctuation run')
  assert.ok(brit['punct:;'] > 0 && yank['punct:;'] === 0)
  assert.ok(yank['emoticon:noncanonical'] > 0, '":-)" is a non-canonical emoticon')
})

test('stylometry: recognises an author from a new sample', () => {
  const authors = { brit: F.FORMAL_BRIT, yank: F.CASUAL_AMERICAN }
  assert.equal(S.identify(F.FORMAL_BRIT_2, authors).best, 'brit')
  assert.equal(S.identify(F.CASUAL_AMERICAN_2, authors).best, 'yank')
})

test('classifier: a physical keyboard is human', () => {
  const r = C.classify(alice(51))
  assert.equal(r.verdict, 'human')
  assert.ok(r.reasons.some((x) => x.name === 'physical-keys'))
})

test('classifier: a composing phone keyboard is human', () => {
  const r = C.classify(F.gboard(F.SAMPLE, { seed: 52 }))
  assert.equal(r.verdict, 'human')
  assert.ok(r.reasons.some((x) => x.name === 'composition'))
})

test('classifier: ChamBoard Typed playback is spotted by its missing composition', () => {
  const r = C.classify(F.chamboard(F.SAMPLE, { seed: 53 }))
  assert.equal(r.verdict, 'chamboard')
  assert.ok(r.reasons.some((x) => x.name === 'no-composition'))
})

test('classifier: ChamBoard Glide playback commits whole words', () => {
  const r = C.classify(F.chamboard(F.SAMPLE, { seed: 54, style: 'glide' }))
  assert.equal(r.verdict, 'chamboard')
  assert.ok(r.reasons.some((x) => x.name === 'whole-words'))
})

test('classifier: too little typing is unsure', () => {
  assert.equal(C.classify(F.hardware('hi', { typoRate: 0 })).verdict, 'unsure')
})
