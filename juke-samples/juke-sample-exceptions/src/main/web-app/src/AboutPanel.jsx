/*
 * A short explainer reachable only via the footer "About this demo" link, which
 * the automated journey never clicks. That keeps its lines uncovered in the UI
 * report, so the UI coverage figure stays realistically below 100% and the
 * threshold gate in the coverage popup has something meaningful to evaluate.
 */
export default function AboutPanel() {
  return (
    <div className="about-panel" data-test="about-panel">
      <h3>About this demo</h3>
      <p>
        This store places three orders with an Order Management System reached
        through a <code>@Juke</code> seam. The same journey is driven four times:
      </p>
      <ol>
        <li><strong>Record</strong> the happy path — three completed orders.</li>
        <li><strong>Replay</strong> — the same three confirmations, deterministically.</li>
        <li><strong>Replay + delay</strong> — a 10s delay injected on the second
            order; the UI shows “Your order is queued”.</li>
        <li><strong>Replay + exception</strong> — an exception injected on the
            second order; the UI shows “technical difficulties”.</li>
      </ol>
      <p>
        Each confirmation number is generated fresh and marked
        <code> @JukeIgnorable</code>, so replay stays a clean deterministic
        repeat even though the number changes every run.
      </p>
    </div>
  )
}
