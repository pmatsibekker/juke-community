import { useEffect, useState } from 'react'
import axios from 'axios'

/*
 * Three-step user journey:
 *   1. Enter a name
 *   2. Pick a style (Formal / Casual / Royal)
 *   3. Submit → see the generated greeting
 *
 * Each step is its own component below so the React build has distinct
 * functions for Istanbul to count. The Royal branch on the server is the
 * less-traveled path — picking it lights up the matching JaCoCo branch on
 * the dashboard.
 */

export default function JourneyApp() {
  const [step,   setStep]   = useState('login')
  const [name,   setName]   = useState('')
  const [style,  setStyle]  = useState(null)
  const [result, setResult] = useState(null)
  const [error,  setError]  = useState(null)

  const reset = () => {
    setStep('login'); setName(''); setStyle(null); setResult(null); setError(null)
  }

  const submit = async () => {
    setError(null)
    try {
      const res = await axios.get('/api/greeting', { params: { name, style } })
      setResult(res.data)
      setStep('done')
    } catch (e) {
      setError(e.message || String(e))
    }
  }

  switch (step) {
    case 'login':
      return <LoginStep name={name} setName={setName} onNext={() => setStep('style')} />
    case 'style':
      return <StyleStep onPick={(s) => { setStyle(s); setStep('confirm') }}
                        onBack={() => setStep('login')} />
    case 'confirm':
      return <ConfirmStep name={name} style={style}
                          onSubmit={submit}
                          onBack={() => setStep('style')} />
    case 'done':
      return <ResultStep result={result} error={error} onReset={reset} />
    default:
      return null
  }
}

// ── Step 1 ──────────────────────────────────────────────────────────────
function LoginStep({ name, setName, onNext }) {
  return (
    <div className="step">
      <p className="step-marker">Step 1 of 3</p>
      <h3>Who are you?</h3>
      <label>
        Your name
        <input
          data-test="name-input"
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="Ada Lovelace"
          autoFocus
        />
      </label>
      <div className="step-actions">
        <button data-test="next-to-style" disabled={!name.trim()} onClick={onNext}>
          Next →
        </button>
      </div>
    </div>
  )
}

// ── Step 2 ──────────────────────────────────────────────────────────────
function StyleStep({ onPick, onBack }) {
  const [styles, setStyles] = useState([])

  useEffect(() => {
    // GET /api/styles is itself a covered server endpoint — coverage rises
    // a little just by reaching this screen.
    axios.get('/api/styles').then(r => setStyles(r.data)).catch(() => {})
  }, [])

  return (
    <div className="step">
      <p className="step-marker">Step 2 of 3</p>
      <h3>Pick a greeting style</h3>
      <div className="style-grid">
        {styles.map(s => (
          <button
            key={s}
            data-test={'style-' + s}
            className={'style-button style-' + s}
            onClick={() => onPick(s)}
          >
            {s[0].toUpperCase() + s.slice(1)}
          </button>
        ))}
      </div>
      <p className="hint">
        Tip: the <strong>Royal</strong> branch is the less-traveled server
        path — picking it raises server branch coverage noticeably.
      </p>
      <div className="step-actions">
        <button className="secondary" onClick={onBack}>← Back</button>
      </div>
    </div>
  )
}

// ── Step 3 ──────────────────────────────────────────────────────────────
function ConfirmStep({ name, style, onSubmit, onBack }) {
  return (
    <div className="step">
      <p className="step-marker">Step 3 of 3</p>
      <h3>Confirm</h3>
      <p>Greet <strong>{name}</strong> in <strong>{style}</strong> style?</p>
      <div className="step-actions">
        <button className="secondary" onClick={onBack}>← Back</button>
        <button data-test="submit" onClick={onSubmit}>Generate greeting</button>
      </div>
    </div>
  )
}

// ── Result ──────────────────────────────────────────────────────────────
function ResultStep({ result, error, onReset }) {
  return (
    <div className="step">
      <p className="step-marker">Done</p>
      <h3>Your greeting</h3>
      {error
        ? <p className="error">Request failed: {error}</p>
        : <blockquote data-test="greeting-text">{result?.content}</blockquote>}
      <div className="step-actions">
        <button data-test="reset" onClick={onReset}>Start over</button>
      </div>
    </div>
  )
}
