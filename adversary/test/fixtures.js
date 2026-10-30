// Fixture sessions for the matcher tests (see docs/design/testing.md). Built in code from a seeded
// generator so they are reproducible and readable, in the same shape recorder.js writes.
//
// Three ways text reaches a web page:
// - hardware(): a person on a physical keyboard (real key names, keyup after keydown);
// - gboard(): a person on a typical phone keyboard (keyCode 229, words composed on screen);
// - chamboard(): ChamBoard replaying a draft (keyCode 229, text committed directly;
//   Typed = one character per commit, Glide = one word per commit; see docs/design/engine.md, Timing).
'use strict'
const R = require('../recorder.js')

// Small seeded generator (mulberry32): fixtures only, never part of the page.
function rng(seed) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function gaussian(rand) {
  const u = Math.max(rand(), 1e-9)
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * rand())
}

// Log-normal gap around `median` ms with spread `sigma`, never below `floor`.
function gap(rand, median, sigma, floor) {
  return Math.max(floor, median * Math.exp(sigma * gaussian(rand)))
}

function builder(label) {
  const rec = R.createRecorder({ label, startedAt: '2026-10-13T00:00:00.000Z' })
  const box = { value: '', selectionStart: 0, selectionEnd: 0 }
  let t = 1000
  const at = () => t
  return {
    wait(ms) {
      t += ms
    },
    event(e) {
      rec.record(Object.assign({ timeStamp: at() }, e), box)
    },
    insert(text, inputType) {
      box.value += text
      box.selectionStart = box.selectionEnd = box.value.length
      rec.record({ type: 'input', timeStamp: at(), inputType: inputType || 'insertText', data: text }, box)
    },
    compose(word) {
      // insertCompositionText replaces the composing word with its longer version.
      const base = box.value.slice(0, box.value.length - (word.length - 1))
      box.value = base + word
      box.selectionStart = box.selectionEnd = box.value.length
      rec.record({ type: 'input', timeStamp: at(), inputType: 'insertCompositionText', data: word, isComposing: true }, box)
    },
    backspace() {
      box.value = box.value.slice(0, -1)
      box.selectionStart = box.selectionEnd = box.value.length
      rec.record({ type: 'input', timeStamp: at(), inputType: 'deleteContentBackward', data: null }, box)
    },
    session: () => rec.session(),
  }
}

// A person on a physical keyboard. `fast` letter pairs are typed in a third of the usual time:
// the kind of personal habit keystroke matchers latch onto.
function hardware(text, opts) {
  const o = Object.assign({ seed: 1, median: 160, sigma: 0.35, floor: 35, fast: [], typoRate: 0.03 }, opts)
  const rand = rng(o.seed)
  const b = builder(o.label || 'hardware')
  let prev = ''
  for (const ch of text) {
    const pair = (prev + ch).toLowerCase()
    b.wait(gap(rand, o.fast.includes(pair) ? o.median / 3 : o.median, o.sigma, o.floor))
    if (rand() < o.typoRate && /[a-z]/.test(ch)) {
      // Two wrong letters, then two backspaces: people often notice a beat late.
      for (const wrong of ['x', 'q']) {
        b.event({ type: 'keydown', key: wrong, code: 'Key' + wrong.toUpperCase(), keyCode: 88 })
        b.insert(wrong)
        b.wait(gap(rand, o.median, o.sigma, o.floor))
      }
      for (let i = 0; i < 2; i++) {
        b.event({ type: 'keydown', key: 'Backspace', code: 'Backspace', keyCode: 8 })
        b.backspace()
        b.wait(gap(rand, o.median, o.sigma, o.floor))
      }
    }
    b.event({ type: 'keydown', key: ch, code: 'Key' + ch.toUpperCase(), keyCode: ch.toUpperCase().charCodeAt(0) })
    b.event({ type: 'beforeinput', inputType: 'insertText', data: ch })
    b.insert(ch)
    b.wait(gap(rand, 90, 0.2, 40))
    b.event({ type: 'keyup', key: ch, code: 'Key' + ch.toUpperCase(), keyCode: ch.toUpperCase().charCodeAt(0) })
    prev = ch
  }
  return b.session()
}

// A person on a phone keyboard that composes each word on screen, then commits a space.
function gboard(text, opts) {
  const o = Object.assign({ seed: 2, median: 220, sigma: 0.45, floor: 45 }, opts)
  const rand = rng(o.seed)
  const b = builder(o.label || 'gboard')
  const words = text.split(' ')
  words.forEach((word, i) => {
    b.event({ type: 'compositionstart', data: '' })
    for (let n = 1; n <= word.length; n++) {
      b.wait(gap(rand, o.median, o.sigma, o.floor))
      b.event({ type: 'keydown', key: 'Unidentified', keyCode: 229 })
      b.compose(word.slice(0, n))
    }
    b.event({ type: 'compositionend', data: word })
    if (i < words.length - 1) {
      b.wait(gap(rand, o.median, o.sigma, o.floor))
      b.event({ type: 'keydown', key: 'Unidentified', keyCode: 229 })
      b.insert(' ')
    }
  })
  return b.session()
}

// ChamBoard replaying a draft: Typed (per character, with an occasional one-letter typo,
// TIM-8) or Glide (per word).
function chamboard(text, opts) {
  const o = Object.assign({ seed: 3, style: 'typed', median: 190, sigma: 0.35, floor: 30, typoRate: 0.02 }, opts)
  const rand = rng(o.seed)
  const b = builder(o.label || 'chamboard-' + o.style)
  const commit = (s) => {
    b.event({ type: 'keydown', key: 'Unidentified', keyCode: 229 })
    b.insert(s)
  }
  if (o.style === 'glide') {
    text.split(/(?<= )/).forEach((chunk) => {
      b.wait(gap(rand, o.median * chunk.length, o.sigma, o.floor))
      commit(chunk)
    })
    return b.session()
  }
  for (const ch of text) {
    b.wait(gap(rand, o.median, o.sigma, o.floor))
    if (rand() < o.typoRate && /[a-z]/.test(ch)) {
      commit('p')
      b.wait(gap(rand, o.median * 2, o.sigma, o.floor))
      b.event({ type: 'keydown', key: 'Unidentified', keyCode: 229 })
      b.backspace()
      b.wait(gap(rand, o.median, o.sigma, o.floor))
    }
    commit(ch)
  }
  return b.session()
}

const SAMPLE =
  'the weather this morning was better than expected so we walked along the river and then ' +
  'stopped for coffee in the small square near the theatre where they sell the best cake in town'

// The two authors from core's StyleFingerprintTest, plus a second sample from each.
const FORMAL_BRIT = [
  'I have been thinking about the proposal; furthermore, I do not believe it is workable… The colour scheme is grey and the programme is poorly organised!!!',
  'Moreover, the centre of the plan is weak. It is extremely unclear what we are doing – nobody has realised the cost.',
  'Nevertheless, I am willing to try again tomorrow; I shall be at the theatre until six. Thus we should meet afterwards.',
  'In addition, I do not think the travelling budget is sensible. I have not seen a proper analysis, and I cannot recommend it.',
].join('\n')

const CASUAL_AMERICAN = [
  'ive been thinking about the proposal and honestly i dont think its workable lol. the color scheme is gray and the program is kinda badly organized :-)',
  'also the center of the plan is weak. its super unclear what were doing -- nobody realized the cost tbh.',
  'still, im willing to try again tmrw, ill be at the theater til six. so we should meet after haha.',
  'plus i dont think the traveling budget makes sense. havent seen a real analysis and i cant recommend it.',
].join('\n')

const FORMAL_BRIT_2 =
  'I have read the report; however, I do not agree with its conclusions… The programme is behind schedule and the centre has not recognised the problem!!! I shall raise it on Monday.'

const CASUAL_AMERICAN_2 =
  'ok so i read the report and i dont really agree lol. the program is behind and the center hasnt realized it :-) ill bring it up monday i guess'

module.exports = {
  rng,
  hardware,
  gboard,
  chamboard,
  SAMPLE,
  FORMAL_BRIT,
  CASUAL_AMERICAN,
  FORMAL_BRIT_2,
  CASUAL_AMERICAN_2,
}
