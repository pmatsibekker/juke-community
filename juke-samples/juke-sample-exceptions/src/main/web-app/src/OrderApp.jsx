import { useEffect, useState } from 'react'
import axios from 'axios'

/*
 * The ordering journey:
 *   1. Pick a product
 *   2. Click "Buy" → the app places THREE orders with the OMS, one after
 *      another (a product ships in three fulfilment orders)
 *   3. As each order resolves, a confirmation popup appears for 5 seconds and
 *      the order is appended to the on-screen log
 *
 * Each order carries a freshly generated confirmation number. Three outcomes
 * are possible per order:
 *   - COMPLETED — the OMS accepted it (happy path / deterministic replay)
 *   - QUEUED    — the OMS is taking too long (a delay injected by Remix); the
 *                 request times out CLIENT-SIDE and we tell the customer their
 *                 order is queued. The confirmation number still stands because
 *                 we generated it before calling the server.
 *   - RECORDED  — the OMS threw (an exception injected by Remix); the server
 *                 caught it, recorded the order for later, and returned the
 *                 "technical difficulties" message.
 */

const ORDERS_PER_PURCHASE = 3
const ORDER_TIMEOUT_MS    = 4000   // client gives up before the 10s injected delay → "queued"
const POPUP_MS            = 5000   // each confirmation popup stays up 5 seconds
const INTER_ORDER_MS      = 800    // small gap so each popup is clearly its own

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

function newConfirmationNumber() {
  const rnd = Math.floor(Math.random() * 0xFFFFFF).toString(16).toUpperCase().padStart(6, '0')
  return 'JK-' + rnd
}

function isTimeout(err) {
  return err?.code === 'ECONNABORTED' || /timeout/i.test(err?.message || '')
}

export default function OrderApp() {
  const [products, setProducts] = useState([])
  const [sku, setSku]           = useState(null)
  const [running, setRunning]   = useState(false)
  const [progress, setProgress] = useState(null)   // "Placing order 2 of 3…"
  const [popup, setPopup]       = useState(null)    // current confirmation popup
  const [log, setLog]           = useState([])      // accumulated order outcomes

  useEffect(() => {
    axios.get('/api/products')
      .then(r => { setProducts(r.data); if (r.data.length) setSku(r.data[0].sku) })
      .catch(() => {})
  }, [])

  const selected = products.find(p => p.sku === sku)

  async function buy() {
    setRunning(true)
    setLog([])
    for (let i = 1; i <= ORDERS_PER_PURCHASE; i++) {
      setProgress(`Placing order ${i} of ${ORDERS_PER_PURCHASE}…`)
      const confirmation = newConfirmationNumber()
      let entry
      try {
        const res = await axios.post(
          '/api/order',
          { sku, quantity: 1, confirmationNumber: confirmation },
          { timeout: ORDER_TIMEOUT_MS },
        )
        const d = res.data
        entry = {
          index: i,
          status: d.status,                       // COMPLETED or RECORDED
          confirmation: d.confirmationNumber || confirmation,
          omsOrderId: d.omsOrderId || null,
          message: d.message,
        }
      } catch (err) {
        entry = isTimeout(err)
          ? { index: i, status: 'QUEUED', confirmation, omsOrderId: null,
              message: 'Your order is queued and will be confirmed shortly.' }
          : { index: i, status: 'ERROR', confirmation, omsOrderId: null,
              message: err.message || String(err) }
      }
      setLog(prev => [...prev, entry])
      setPopup(entry)
      await sleep(POPUP_MS)      // hold the popup so it can be read
      setPopup(null)
      await sleep(INTER_ORDER_MS)
    }
    setProgress(null)
    setRunning(false)
  }

  return (
    <section className="order-app" data-test="order-app">
      <h2>Choose a product</h2>

      <div className="product-grid">
        {products.map(p => (
          <button
            key={p.sku}
            type="button"
            data-test={'product-' + p.sku}
            className={'product-card' + (p.sku === sku ? ' selected' : '')}
            disabled={running}
            onClick={() => setSku(p.sku)}
          >
            <span className="product-name">{p.name}</span>
            <span className="product-sku">{p.sku}</span>
            <span className="product-price">${(p.priceCents / 100).toFixed(2)}</span>
          </button>
        ))}
      </div>

      <div className="buy-row">
        <button type="button" data-test="buy" className="buy-button" disabled={!sku || running} onClick={buy}>
          {running ? 'Processing…' : selected ? `Buy ${selected.name}` : 'Buy'}
        </button>
        {progress && <span className="progress" data-test="progress">{progress}</span>}
      </div>

      <p className="hint">
        Buying places <strong>3 orders</strong> with the Order Management System.
        Each order confirmation pops up for 5 seconds.
      </p>

      {log.length > 0 && (
        <div className="order-log" data-test="order-log">
          <h3>Orders</h3>
          <ul>
            {log.map(e => (
              <li key={e.index} data-test={'log-row-' + e.index} className={'log-row status-' + e.status.toLowerCase()}>
                <span className="log-index">Order {e.index} of {ORDERS_PER_PURCHASE}</span>
                <span className={'log-status badge-' + e.status.toLowerCase()} data-test={'log-status-' + e.index}>
                  {e.status}
                </span>
                <span className="log-confirmation" data-test={'log-confirmation-' + e.index}>
                  {e.confirmation}
                </span>
                {e.omsOrderId && <span className="log-oms">{e.omsOrderId}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      {popup && <OrderPopup entry={popup} />}
    </section>
  )
}

// ── The 5-second confirmation popup ──────────────────────────────────────────
function OrderPopup({ entry }) {
  const titles = {
    COMPLETED: 'Order completed',
    QUEUED:    'Your order is queued',
    RECORDED:  'Technical difficulties',
    ERROR:     'Something went wrong',
  }
  return (
    <div className="popup-backdrop" data-test="order-popup">
      <div className={'popup popup-' + entry.status.toLowerCase()}>
        <p className="popup-order" data-test="popup-order-index">
          Order {entry.index} of {ORDERS_PER_PURCHASE}
        </p>
        <h3 className="popup-title" data-test="popup-status" data-status={entry.status}>
          {titles[entry.status] || entry.status}
        </h3>
        <p className="popup-message" data-test="popup-message">{entry.message}</p>
        <p className="popup-confirmation">
          Confirmation number:{' '}
          <strong data-test="popup-confirmation">{entry.confirmation}</strong>
        </p>
        {entry.omsOrderId && (
          <p className="popup-oms">
            OMS order id: <code data-test="popup-oms">{entry.omsOrderId}</code>
          </p>
        )}
      </div>
    </div>
  )
}
