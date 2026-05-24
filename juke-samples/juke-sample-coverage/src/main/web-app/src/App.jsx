import { useState } from 'react'
import CoveragePanel from './CoveragePanel'
import JourneyApp from './JourneyApp'
import AboutScreen from './AboutScreen'

/*
 * Top-level layout — the journey on the left, the live coverage dashboard
 * on the right. As the user clicks through the journey both halves of the
 * coverage figure climb.
 *
 * `AboutScreen` is imported but reachable only via a footer link the demo
 * flow never clicks, so its lines stay uncovered in the UI report — that's
 * what keeps UI line-coverage realistically below 100%.
 */
const App = () => {
  const [showAbout, setShowAbout] = useState(false)

  return (
    <div className="layout">
      <header>
        <h1>Juke Coverage Demo</h1>
        <p className="subtitle">
          Click through the journey on the left. Watch the dashboard on the
          right light up as the calls you make exercise more lines of the
          server and the UI.
        </p>
      </header>

      <div className="cols">
        <section className="col-app" data-test="journey-col">
          <h2>Application</h2>
          <JourneyApp />
        </section>

        <aside className="col-coverage" data-test="coverage-col">
          <CoveragePanel />
        </aside>
      </div>

      <footer>
        <button
          type="button"
          className="link-button"
          data-test="about-link"
          onClick={() => setShowAbout(s => !s)}
        >
          {showAbout ? 'Hide about' : 'About this demo'}
        </button>
        {showAbout && <AboutScreen />}
      </footer>
    </div>
  )
}

export default App
