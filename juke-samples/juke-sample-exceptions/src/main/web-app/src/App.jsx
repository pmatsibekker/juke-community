import { useState } from 'react'
import OrderApp from './OrderApp'
import CoverageModal from './CoverageModal'
import AboutPanel from './AboutPanel'

/*
 * Top-level layout. The ordering screen is the whole app; test-coverage
 * statistics live in a SEPARATE popup opened from the toolbar, deliberately
 * kept off the ordering screen so the demo's application UI and its
 * testing/observability surface stay logically separated.
 */
const App = () => {
  const [showCoverage, setShowCoverage] = useState(false)
  const [showAbout, setShowAbout] = useState(false)

  return (
    <div className="layout">
      <header className="app-header">
        <div>
          <h1>Juke Store</h1>
          <p className="subtitle">Buy a product — we place your order with the Order Management System.</p>
        </div>
        <button
          type="button"
          className="coverage-button"
          data-test="view-coverage"
          onClick={() => setShowCoverage(true)}
        >
          View test coverage
        </button>
      </header>

      <main>
        <OrderApp />
      </main>

      <footer>
        <button
          type="button"
          className="link-button"
          data-test="about-link"
          onClick={() => setShowAbout(s => !s)}
        >
          {showAbout ? 'Hide details' : 'About this demo'}
        </button>
        {showAbout && <AboutPanel />}
      </footer>

      {showCoverage && <CoverageModal onClose={() => setShowCoverage(false)} />}
    </div>
  )
}

export default App
