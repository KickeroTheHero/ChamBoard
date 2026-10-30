// Stylometry matcher for the adversarial test page (see docs/design/testing.md).
//
// Plays a tracker that recognises people by *how* they write: punctuation, casing,
// contractions, regional spelling, emoticons and how often they use common little words. The
// feature families mirror core's StyleFingerprint (the evaluation stand-in; see docs/design/testing.md) so
// the page and the engine tests measure the same habits, but this is the attacker's own simple
// version: it knows nothing about ChamBoard's lexicons.
;(function (root, factory) {
  const api = factory()
  if (typeof module === 'object' && module.exports) {
    module.exports = api
  } else {
    root.AdversaryStylometry = api
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict'

  const FUNCTION_WORDS = [
    'the', 'a', 'and', 'but', 'so', 'also', 'however', 'very', 'really', 'just', 'not', 'that',
    'which', 'because', 'though', 'while', 'among', 'maybe', 'perhaps', 'yes',
  ]

  // Punctuation families: "!!!" and "!" or "…" and "..." count as the same habit.
  const PUNCTUATION = {
    '!': (p) => p.includes('!'),
    '?': (p) => p.includes('?'),
    ',': (p) => p.includes(','),
    ';': (p) => p === ';',
    ':': (p) => p === ':',
    '...': (p) => p === '…' || p.startsWith('..'),
    dash: (p) => /^[-–—]+$/.test(p),
    quote: (p) => /^["'‘’“”„‟«»]+$/.test(p),
    '(': (p) => p === '(',
  }

  const CANONICAL_EMOTICONS = new Set([':)', ':(', ':D', ';)', ':P', ':/', ':O', '<3'])
  const EMOTICON = /(?:[:;=]-?[)(DPpO/\\|]|<3)/g
  const WORD = /[A-Za-z]+(?:['’][A-Za-z]+)*/g

  // Contractions with and without the apostrophe ("dont" is as telling as "don't").
  const BARE_CONTRACTIONS = new Set([
    'dont', 'doesnt', 'didnt', 'isnt', 'arent', 'wasnt', 'werent', 'havent', 'hasnt', 'hadnt',
    'wouldnt', 'shouldnt', 'couldnt', 'mustnt', 'im', 'ive', 'youre', 'youve', 'theyre', 'theyve',
    'thats', 'whats', 'theres', 'hes', 'shes', 'id', 'youd',
  ])

  // A handful of British/American pairs. Real trackers use thousands; a few are enough to show
  // the habit leaking.
  const BRITISH = /^(colours?|grey|centres?|theatres?|programmes?|travelling|travelled|organis\w*|realis\w*|recognis\w*|analys(e|ed|es|ing)|favourite\w*|behaviour\w*|honour\w*|metres?|defence)$/
  const AMERICAN = /^(colors?|gray|centers?|theaters?|programs?|traveling|traveled|organiz\w*|realiz\w*|recogniz\w*|analyz\w*|favorite\w*|behavior\w*|honor\w*|meters?|defense)$/

  function count(list, predicate) {
    let n = 0
    for (const item of list) if (predicate(item)) n++
    return n
  }

  // Fraction of sentences that start with a lowercase word.
  function lowercaseStarts(text) {
    const sentences = text.split(/[.!?…]+\s+|\n+/)
    let starts = 0
    let lower = 0
    for (const s of sentences) {
      const m = s.match(/[A-Za-z]/)
      if (!m) continue
      starts++
      if (m[0] === m[0].toLowerCase()) lower++
    }
    return starts === 0 ? 0 : lower / starts
  }

  // Feature map for one text. Rates are per character for punctuation-like habits and per word
  // for word habits, as in StyleFingerprint.
  function fingerprint(text) {
    const emoticons = text.match(EMOTICON) || []
    const rest = text.replace(EMOTICON, ' ')
    const words = rest.match(WORD) || []
    const punct = rest.replace(WORD, ' ').match(/[^\s\w]+/g) || []
    const chars = Math.max(text.length, 1)
    const wc = Math.max(words.length, 1)
    const lower = words.map((w) => w.toLowerCase().replace(/’/g, "'"))
    const f = {}
    for (const [name, family] of Object.entries(PUNCTUATION)) {
      f['punct:' + name] = count(punct, family) / chars
    }
    f['punct:runs'] = count(punct, (p) => p.length > 1 && p !== '...') / chars
    f['space:double'] = (text.match(/ {2,}/g) || []).length / chars
    f.curly = (text.match(/[‘’“”]/g) || []).length / chars
    f['case:lower-i'] = count(words, (w) => w === 'i' || /^i['’]/.test(w)) / wc
    f['case:lower-start'] = lowercaseStarts(text)
    f['contraction:rate'] = count(lower, (w) => /'/.test(w) || BARE_CONTRACTIONS.has(w)) / wc
    f['spelling:british'] = count(lower, (w) => BRITISH.test(w)) / wc
    f['spelling:american'] = count(lower, (w) => AMERICAN.test(w)) / wc
    f['emoticon:noncanonical'] = count(emoticons, (e) => !CANONICAL_EMOTICONS.has(e)) / chars
    for (const w of FUNCTION_WORDS) f['fw:' + w] = count(lower, (x) => x === w) / wc
    return f
  }

  // Canberra distance: scale-free, 0 = identical, grows by up to 1 per differing feature.
  function distance(a, b) {
    let d = 0
    for (const k of Object.keys(a)) {
      const x = a[k]
      const y = b[k] || 0
      if (x !== 0 || y !== 0) d += Math.abs(x - y) / (Math.abs(x) + Math.abs(y))
    }
    return d
  }

  // Ranks known authors by distance to `text`, closest first. `authors` maps a name to sample
  // text. `margin` is how far ahead the winner is, relative to the runner-up (0 = a tie).
  function identify(text, authors) {
    const probe = fingerprint(text)
    const ranked = Object.entries(authors)
      .map(([name, sample]) => ({ name, distance: distance(probe, fingerprint(sample)) }))
      .sort((p, q) => p.distance - q.distance)
    const margin =
      ranked.length > 1 && ranked[1].distance > 0
        ? (ranked[1].distance - ranked[0].distance) / ranked[1].distance
        : 0
    return { best: ranked.length ? ranked[0].name : null, margin, ranked }
  }

  return { FUNCTION_WORDS, fingerprint, distance, identify }
})
