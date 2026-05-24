/*
 * "About" page reachable only via the footer link. The default Playwright
 * journey doesn't click that link, so this component's render function is
 * never executed in a typical demo run — Istanbul reports it as uncovered
 * and that keeps UI line-coverage realistically below 100%.
 *
 * Hit the "About this demo" button after running the journey to see the
 * lines turn green in the UI drill-down.
 */
export default function AboutScreen() {
  const facts = [
    'The dashboard polls /service/coverage every 2 seconds.',
    'Server coverage is read live in-process via the JaCoCo agent.',
    'UI coverage comes from window.__coverage__, written out by nyc.',
    'Both endpoints always respond 200 — `available` carries failure detail.',
    'Pass `juke.coverage.threshold.*` to gate CI on a single boolean.',
  ]
  return (
    <div className="about">
      <h3>About this demo</h3>
      <ul>
        {facts.map((f, i) => <li key={i}>{f}</li>)}
      </ul>
    </div>
  )
}
