// Wires the recorder to the page (see docs/design/testing.md). All the logic worth testing lives in
// recorder.js; this file only touches the DOM.
;(function () {
  'use strict'
  const R = globalThis.AdversaryRecorder
  const field = document.getElementById('field')
  const label = document.getElementById('label')
  const table = document.getElementById('events')
  const summary = document.getElementById('summary')
  let recorder = fresh()

  function fresh() {
    return R.createRecorder({
      label: label.value,
      startedAt: new Date().toISOString(),
      userAgent: navigator.userAgent,
    })
  }

  function cell(row, text) {
    const td = document.createElement('td')
    td.textContent = text === null || text === undefined ? '' : String(text)
    row.appendChild(td)
  }

  function showVerdict(session) {
    const result = globalThis.AdversaryClassifier.classify(session)
    document.getElementById('verdict').textContent = result.verdict + ' (score ' + result.score + ')'
    const list = document.getElementById('reasons')
    list.replaceChildren()
    for (const r of result.reasons) {
      const li = document.createElement('li')
      li.textContent = (r.weight > 0 ? '+' : '') + r.weight + ' ' + r.why
      list.appendChild(li)
    }
  }

  function showSummary() {
    const session = recorder.session()
    showVerdict(session)
    const s = R.summarize(session)
    summary.replaceChildren()
    for (const [name, value] of Object.entries(s)) {
      const dt = document.createElement('dt')
      dt.textContent = name
      const dd = document.createElement('dd')
      dd.textContent = String(Math.round(value * 10) / 10)
      summary.append(dt, dd)
    }
  }

  function show(entry) {
    const row = document.createElement('tr')
    cell(row, entry.t.toFixed(1))
    cell(row, entry.type)
    cell(row, entry.key !== undefined ? entry.key : entry.inputType)
    cell(row, entry.keyCode)
    cell(row, entry.data === null || entry.data === undefined ? '' : JSON.stringify(entry.data))
    cell(row, entry.value !== undefined ? entry.value.length : '')
    table.appendChild(row)
    showSummary()
  }

  for (const type of R.EVENT_TYPES) {
    field.addEventListener(type, (event) => {
      const entry = recorder.record(event, field)
      if (entry) show(entry)
    })
  }

  document.getElementById('clear').addEventListener('click', () => {
    field.value = ''
    table.replaceChildren()
    recorder = fresh()
    showSummary()
  })

  // A Blob URL keeps the download on this device: no request leaves the page.
  document.getElementById('download').addEventListener('click', () => {
    const session = recorder.session()
    session.label = label.value
    const blob = new Blob([JSON.stringify(session, null, 2)], { type: 'application/json' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = 'session-' + (session.startedAt || 'unknown').replace(/[:.]/g, '-') + '.json'
    link.click()
    setTimeout(() => URL.revokeObjectURL(link.href), 1000)
  })

  showSummary()
})()
