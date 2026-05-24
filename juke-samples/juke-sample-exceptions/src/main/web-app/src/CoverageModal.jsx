import { useEffect, useState } from 'react'
import axios from 'axios'

/*
 * Test-coverage popup. Deliberately a modal, separate from the ordering screen:
 * the coverage figure describes the TESTS that exercise the app, not the app
 * itself, so it is kept off the application UI.
 *
 * It polls GET /service/coverage every 2 seconds and shows both halves (server
 * JaCoCo + UI nyc/Istanbul), the @Juke seams excluded from the figure, and the
 * pass/fail badge. A second tab embeds the full drill-down HTML report — the
 * Playwright spec opens it and holds it on screen for >10 seconds so the result
 * is readable.
 */
const POLL_MS = 2000

const SERVER_METRICS = [
  { key: 'line',        label: 'Line' },
  { key: 'branch',      label: 'Branch' },
  { key: 'instruction', label: 'Instruction' },
]
const UI_METRICS = [
  { key: 'lines',      label: 'Lines' },
  { key: 'statements', label: 'Statements' },
  { key: 'functions',  label: 'Functions' },
  { key: 'branches',   label: 'Branches' },
]

export default function CoverageModal({ onClose }) {
  const [data, setData]   = useState(null)
  const [error, setError] = useState(null)
  const [view, setView]   = useState('summary')   // 'summary' | 'report'
  const [report, setReport] = useState('server')  // 'server' | 'ui'

  useEffect(() => {
    let cancelled = false
    const tick = async () => {
      try {
        const res = await axios.get('/service/coverage')
        if (!cancelled) { setData(res.data); setError(null) }
      } catch (e) {
        if (!cancelled) setError(e.message || String(e))
      }
    }
    tick()
    const id = setInterval(tick, POLL_MS)
    return () => { cancelled = true; clearInterval(id) }
  }, [])

  return (
    <div className="modal-backdrop" data-test="coverage-modal" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="modal-head">
          <h2>
            Test coverage
            {data && (
              <span className={'passed-badge ' + (data.passed ? 'passed' : 'failed')} data-test="passed-badge">
                {data.passed ? 'PASSED' : 'FAILED'}
              </span>
            )}
          </h2>
          <button type="button" className="close-button" data-test="close-coverage" onClick={onClose}>×</button>
        </div>

        <div className="tabs">
          <button type="button" data-test="tab-summary"
                  className={'tab' + (view === 'summary' ? ' active' : '')}
                  onClick={() => setView('summary')}>Summary</button>
          <button type="button" data-test="open-coverage-report"
                  className={'tab' + (view === 'report' ? ' active' : '')}
                  onClick={() => setView('report')}>Coverage report ↗</button>
        </div>

        <div className="modal-body">
          {view === 'summary'
            ? <Summary data={data} error={error} />
            : <Report report={report} setReport={setReport} />}
        </div>
      </div>
    </div>
  )
}

function Summary({ data, error }) {
  if (error) {
    return (
      <div>
        <p className="error">Couldn't reach /service/coverage: {error}</p>
        <p>
          The juke-coverage module is only mounted when
          <code> juke.coverage.enabled=true</code>. Start the server with
          <code> demo-start-server</code> to enable it.
        </p>
      </div>
    )
  }
  if (!data) return <p>Loading…</p>

  return (
    <div data-test="coverage-summary">
      <CoverageHalf label="Server (JaCoCo)"   half={data.server} metrics={SERVER_METRICS} />
      <CoverageHalf label="UI (nyc/Istanbul)" half={data.ui}     metrics={UI_METRICS} />

      {data.server?.available && data.server.excludedSeams?.length > 0 && (
        <div className="seams">
          <h3>@Juke seams excluded from the figure</h3>
          <ul>
            {data.server.excludedSeams.map((s, i) => <li key={i}><code>{s}</code></li>)}
          </ul>
          <p className="hint">
            The OMS implementation is proxied away in replay mode, so counting it
            would unfairly depress server coverage.
          </p>
        </div>
      )}

      {data.generatedAt && (
        <p className="generated">Refreshed {new Date(data.generatedAt).toLocaleTimeString()}.</p>
      )}
    </div>
  )
}

function CoverageHalf({ label, half, metrics }) {
  if (!half || !half.available) {
    return (
      <section className="coverage-half">
        <h3>{label}</h3>
        <p className="unavailable">Not available — {half?.message || 'no data yet.'}</p>
      </section>
    )
  }
  return (
    <section className="coverage-half">
      <h3>{label}</h3>
      {metrics.map(({ key, label: m }) => <Bar key={key} label={m} value={half[key]} />)}
      {!half.passed && half.message && <p className="threshold-message">{half.message}</p>}
    </section>
  )
}

function Bar({ label, value }) {
  const v = typeof value === 'number' ? value : 0
  const width = Math.max(2, Math.min(100, v)) + '%'
  return (
    <div className="bar-row" data-test={'bar-' + label.toLowerCase()}>
      <span className="bar-label">{label}</span>
      <div className="bar-track">
        <div className="bar-fill" style={{ width }}>{v.toFixed(1)}%</div>
      </div>
    </div>
  )
}

function Report({ report, setReport }) {
  const src = report === 'server' ? '/coverage/server/index.html' : '/coverage/ui/index.html'
  return (
    <div className="report-view">
      <div className="report-switch">
        <label>
          <input type="radio" name="report" checked={report === 'server'}
                 data-test="report-server" onChange={() => setReport('server')} />
          Server (JaCoCo)
        </label>
        <label>
          <input type="radio" name="report" checked={report === 'ui'}
                 data-test="report-ui" onChange={() => setReport('ui')} />
          UI (nyc/Istanbul)
        </label>
      </div>
      <iframe
        title="coverage-report"
        data-test="coverage-report-frame"
        className="report-frame"
        src={src}
      />
    </div>
  )
}
