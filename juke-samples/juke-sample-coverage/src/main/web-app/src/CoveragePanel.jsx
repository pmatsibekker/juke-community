import { useEffect, useState } from 'react'
import axios from 'axios'

/*
 * Live coverage dashboard. Polls GET /service/coverage every 2 seconds and
 * renders both halves of the response with progress bars, the @Juke
 * exclusion list, and the top-level pass/fail badge.
 *
 * The endpoint always returns 200 — when either half is unavailable (no
 * agent attached, no Playwright run yet) the body carries `available: false`
 * and a `message` we display in italics. The dashboard never errors.
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

export default function CoveragePanel() {
  const [data, setData]   = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    const tick = async () => {
      try {
        const res = await axios.get('/service/coverage')
        if (!cancelled) {
          setData(res.data)
          setError(null)
        }
      } catch (e) {
        if (!cancelled) setError(e.message || String(e))
      }
    }
    tick()
    const id = setInterval(tick, POLL_MS)
    return () => { cancelled = true; clearInterval(id) }
  }, [])

  if (error) {
    return (
      <div>
        <h2>Live Coverage</h2>
        <p className="error">Couldn't reach /service/coverage: {error}</p>
        <p>
          The juke-coverage module is only mounted when
          <code> juke.coverage.enabled=true</code>. Start the server with
          <code> demo-start-server</code> to enable it.
        </p>
      </div>
    )
  }

  if (!data) return <div><h2>Live Coverage</h2><p>Loading…</p></div>

  return (
    <div>
      <h2>
        Live Coverage
        <span className={'passed-badge ' + (data.passed ? 'passed' : 'failed')}
              data-test="passed-badge">
          {data.passed ? 'PASSED' : 'FAILED'}
        </span>
      </h2>

      <CoverageHalf label="Server (JaCoCo)"   half={data.server} metrics={SERVER_METRICS} />
      <CoverageHalf label="UI (nyc/Istanbul)" half={data.ui}     metrics={UI_METRICS} />

      {data.server.available && data.server.excludedSeams?.length > 0 && (
        <div className="seams">
          <h3>@Juke seams excluded from the figure</h3>
          <ul>
            {data.server.excludedSeams.map((s, i) => <li key={i}><code>{s}</code></li>)}
          </ul>
          <p className="hint">
            These are the implementation classes Juke proxies away in replay
            mode. Counting them would unfairly depress server coverage.
          </p>
        </div>
      )}

      <div className="drilldown">
        <a href="/coverage/server/index.html" target="_blank" rel="noopener">
          Open server drill-down ↗
        </a>
        {' · '}
        <a href="/coverage/ui/index.html" target="_blank" rel="noopener">
          Open UI drill-down ↗
        </a>
      </div>

      <p className="generated">
        Refreshed {new Date(data.generatedAt).toLocaleTimeString()}.
      </p>
    </div>
  )
}

function CoverageHalf({ label, half, metrics }) {
  if (!half || !half.available) {
    return (
      <section className="coverage-half">
        <h3>{label}</h3>
        <p className="unavailable">
          Not available — {half?.message || 'no data yet.'}
        </p>
      </section>
    )
  }
  return (
    <section className="coverage-half">
      <h3>{label}</h3>
      {metrics.map(({ key, label: m }) => (
        <Bar key={key} label={m} value={half[key]} />
      ))}
      {!half.passed && (
        <p className="threshold-message">{half.message}</p>
      )}
    </section>
  )
}

function Bar({ label, value }) {
  // Render a tiny non-zero strip even when the value is 0 so the bar is
  // visible — easier to tell "covered nothing" from "endpoint hasn't replied
  // yet" at a glance.
  const width = Math.max(2, Math.min(100, value)) + '%'
  return (
    <div className="bar-row" data-test={'bar-' + label.toLowerCase()}>
      <span className="bar-label">{label}</span>
      <div className="bar-track">
        <div className="bar-fill" style={{ width }}>
          {value.toFixed(1)}%
        </div>
      </div>
    </div>
  )
}
