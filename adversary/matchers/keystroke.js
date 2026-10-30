// Keystroke-dynamics matcher for the adversarial test page (see docs/design/testing.md).
//
// Plays a tracker that recognises people by their typing rhythm: the time between one change
// to the text box and the next ("flight time"), and how fast they type familiar letter pairs.
// It works on recorded sessions (recorder.js). Every text change counts as one stroke, because
// phone keyboards report no real key names (keyCode 229), so `input` events are the one timing
// signal every keyboard gives a web page.
;(function (root, factory) {
  const api = factory()
  if (typeof module === 'object' && module.exports) {
    module.exports = api
  } else {
    root.AdversaryKeystroke = api
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict'

  // Gaps longer than this are the typist stopping, not typing rhythm.
  const IDLE_MS = 5000
  // Histogram bucket edges in ms, roughly log-spaced so fast and slow typists both spread out.
  const BINS = [0, 50, 80, 120, 170, 240, 340, 480, 680, 1000, 1500, Infinity]
  const MIN_DIGRAPH = 2

  // One stroke per text change: its time and the character it added ('⌫' for a deletion).
  function strokes(session) {
    const out = []
    for (const e of session.events) {
      if (e.type !== 'input') continue
      if (typeof e.inputType === 'string' && e.inputType.startsWith('delete')) {
        out.push({ t: e.t, ch: '⌫' })
      } else if (typeof e.data === 'string' && e.data.length > 0) {
        out.push({ t: e.t, ch: e.data[e.data.length - 1].toLowerCase() })
      }
    }
    return out
  }

  function quantile(sorted, q) {
    if (sorted.length === 0) return 0
    const pos = (sorted.length - 1) * q
    const lo = Math.floor(pos)
    const hi = Math.ceil(pos)
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
  }

  // A typing profile: flight-time quartiles, a histogram, pause rate and mean digraph times.
  function profile(session) {
    const s = strokes(session)
    const flights = []
    const pairs = {}
    let pauses = 0
    for (let i = 1; i < s.length; i++) {
      const gap = s[i].t - s[i - 1].t
      if (gap > IDLE_MS) continue
      flights.push(gap)
      if (gap > 1000) pauses++
      const pair = s[i - 1].ch + s[i].ch
      if (/^[a-z]{2}$/.test(pair)) (pairs[pair] = pairs[pair] || []).push(gap)
    }
    const sorted = flights.slice().sort((a, b) => a - b)
    const histogram = new Array(BINS.length - 1).fill(0)
    for (const f of flights) {
      for (let b = 0; b < histogram.length; b++) {
        if (f >= BINS[b] && f < BINS[b + 1]) {
          histogram[b]++
          break
        }
      }
    }
    const n = Math.max(flights.length, 1)
    const digraphs = {}
    for (const [pair, gaps] of Object.entries(pairs)) {
      if (gaps.length >= MIN_DIGRAPH) digraphs[pair] = gaps.reduce((a, b) => a + b, 0) / gaps.length
    }
    return {
      strokes: s.length,
      flights: flights.length,
      median: quantile(sorted, 0.5),
      q1: quantile(sorted, 0.25),
      q3: quantile(sorted, 0.75),
      pauseRate: pauses / n,
      histogram: histogram.map((c) => c / n),
      digraphs,
    }
  }

  function logRatio(a, b) {
    return Math.abs(Math.log(Math.max(a, 1) / Math.max(b, 1)))
  }

  // How different two profiles are: 0 = same rhythm. Sums the histogram difference (0..1), the
  // speed difference (log ratio of medians and spreads) and, where both typed the same letter
  // pairs, how differently fast they typed them.
  function distance(a, b) {
    let hist = 0
    for (let i = 0; i < a.histogram.length; i++) hist += Math.abs(a.histogram[i] - b.histogram[i])
    let d = hist / 2 + logRatio(a.median, b.median) + logRatio(a.q3 - a.q1, b.q3 - b.q1) / 2
    const shared = Object.keys(a.digraphs).filter((k) => k in b.digraphs)
    if (shared.length > 0) {
      d += shared.reduce((sum, k) => sum + logRatio(a.digraphs[k], b.digraphs[k]), 0) / shared.length
    }
    return d
  }

  // Ranks enrolled typists by distance to `session`, closest first. `enrolled` maps a name to a
  // session (or an already computed profile).
  function identify(session, enrolled) {
    const probe = profile(session)
    const ranked = Object.entries(enrolled)
      .map(([name, s]) => ({ name, distance: distance(probe, s.events ? profile(s) : s) }))
      .sort((p, q) => p.distance - q.distance)
    const margin =
      ranked.length > 1 && ranked[1].distance > 0
        ? (ranked[1].distance - ranked[0].distance) / ranked[1].distance
        : 0
    return { best: ranked.length ? ranked[0].name : null, margin, ranked }
  }

  return { BINS, strokes, profile, distance, identify }
})
