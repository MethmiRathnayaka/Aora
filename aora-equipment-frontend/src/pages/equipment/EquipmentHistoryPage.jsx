import { useAuth0 } from '@auth0/auth0-react'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchTransfers } from '../../api/transferApi'
import RecordName from '../../components/RecordName'

function locationLabel(type, worksiteId) {
  if (type === 'STORE') return 'Store'
  return <RecordName id={worksiteId} />
}

function formatDate(value) {
  if (!value) return 'Pending'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export default function EquipmentHistoryPage() {
  const { getAccessTokenSilently, user, logout } = useAuth0()
  const navigate = useNavigate()
  const { equipmentCode } = useParams()
  const decodedEquipmentCode = decodeURIComponent(equipmentCode || '')
  const [transfers, setTransfers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    async function loadHistory() {
      setLoading(true)
      setError('')
      try {
        const result = await fetchTransfers({
          getAccessTokenSilently,
          equipmentCode: decodedEquipmentCode,
        })
        if (!cancelled) setTransfers(result.content ?? [])
      } catch (requestError) {
        if (!cancelled) setError(requestError.message || 'Unable to load transaction history.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadHistory()
    return () => { cancelled = true }
  }, [decodedEquipmentCode, getAccessTokenSilently])

  return (
    <div className="dashboard-shell">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">AORA EQUIPMENT</p>
          <h1>Transaction history</h1>
          <p className="muted">{decodedEquipmentCode} · Signed in as {user?.name || user?.email}</p>
        </div>
        <div className="header-actions">
          <button className="button button-secondary" onClick={() => navigate('/dashboard')}>Dashboard</button>
          <button className="button button-secondary" onClick={() => navigate('/equipment')}>Equipment</button>
          <button className="button button-secondary" onClick={() => logout({ logoutParams: { returnTo: window.location.origin } })}>Sign out</button>
        </div>
      </header>

      <main className="dashboard-content history-content">
        <section className="equipment-panel">
          <div className="panel-heading">
            <div>
              <h2>Movement history</h2>
              <p className="muted">Every recorded transfer for this equipment.</p>
            </div>
            {loading ? <span className="muted">Loading...</span> : null}
          </div>

          {error ? <p className="state-message error-message">{error}</p> : null}
          {!loading && !error && transfers.length === 0 ? <p className="state-message">No transactions have been recorded for this equipment.</p> : null}
          {!error && transfers.length > 0 ? (
            <div className="history-list">
              {transfers.map((transfer) => {
                const item = transfer.equipment?.[0]
                return (
                  <article className="history-item" key={transfer.transferId}>
                    <div className="history-item-heading">
                      <div>
                        <p className="eyebrow">TRANSFER {transfer.transferId}</p>
                        <h3>{locationLabel(transfer.fromType, transfer.fromWorksiteId)} <span aria-hidden="true">to</span> {locationLabel(transfer.toType, transfer.toWorksiteId)}</h3>
                      </div>
                      <span className="status-badge">{transfer.status}</span>
                    </div>
                    <dl className="history-details">
                      <div><dt>Dispatched</dt><dd>{formatDate(transfer.dispatchedAt)}</dd></div>
                      <div><dt>Received</dt><dd>{formatDate(transfer.receivedAt)}</dd></div>
                      <div><dt>Condition</dt><dd>{item?.conditionBefore || '-'} to {item?.conditionAfter || 'Pending'}</dd></div>
                      <div><dt>Delivery contact</dt><dd>{transfer.deliveryPersonName || '-'}</dd></div>
                    </dl>
                    {transfer.rentTotal != null ? <p>Rental period: {formatDate(transfer.rentalStartedAt)} to {formatDate(transfer.receivedAt)}<br />Rent: {transfer.rentalDays} days x {Number(transfer.unitPrice).toFixed(2)} = <strong>{Number(transfer.rentTotal).toFixed(2)}</strong></p> : null}
                    {transfer.notes ? <p className="history-notes">{transfer.notes}</p> : null}
                  </article>
                )
              })}
            </div>
          ) : null}
        </section>
      </main>
    </div>
  )
}
