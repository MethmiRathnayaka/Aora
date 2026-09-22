import { useEffect, useRef, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { deleteTransfer, fetchTransfers } from '../../api/transferApi'
import RecordName from '../../components/RecordName'

export default function TransferList({ equipmentCode }) {
  const { getAccessTokenSilently } = useAuth0()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('')
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [deleting, setDeleting] = useState(null)
  const busy = useRef(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError('')
      try {
        const data = await fetchTransfers({ getAccessTokenSilently, equipmentCode, status, page, size: 20 })
        if (cancelled) return
        setResult(data)
        if (page > 0 && page >= data.totalPages) setPage(Math.max(0, data.totalPages - 1))
      } catch (failure) {
        if (!cancelled) setError(failure.message || 'Unable to load transfers.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [getAccessTokenSilently, equipmentCode, status, page, attempt])

  async function remove(transfer) {
    if (busy.current) return
    const consequence = transfer.status === 'IN_TRANSIT'
      ? 'Equipment will be restored to its source location.'
      : 'The equipment’s current location and condition will stay unchanged.'
    if (!window.confirm(`Delete transfer ${transfer.transferId}? ${consequence} This permanently removes the transfer record.`)) return
    busy.current = true
    setDeleting(transfer.transferId)
    setError('')
    setMessage('')
    try {
      await deleteTransfer({ getAccessTokenSilently, transferId: transfer.transferId })
      setMessage(`Transfer ${transfer.transferId} deleted.`)
      setAttempt((value) => value + 1)
    } catch (failure) {
      setError(failure.message || 'Unable to delete transfer.')
    } finally {
      busy.current = false
      setDeleting(null)
    }
  }

  const location = (type, id) => type === 'STORE' ? 'Store' : <RecordName id={id} />
  return <section className="equipment-panel transfer-panel">
    <div className="panel-heading"><h2>All transfers</h2><label>Status <select disabled={deleting !== null} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}><option value="">All statuses</option>{['IN_TRANSIT', 'COMPLETED', 'CANCELLED'].map((value) => <option key={value} value={value}>{value.replaceAll('_', ' ')}</option>)}</select></label></div>
    {error ? <p className="error-message" role="alert">{error} <button className="button button-secondary" onClick={() => setAttempt((value) => value + 1)}>Refresh</button></p> : null}
    {message ? <p className="success-message" role="status">{message}</p> : null}
    {loading ? <p role="status">Loading transfers...</p> : <div className="table-wrap"><table>
      <thead><tr><th>Transfer</th><th>Equipment</th><th>From</th><th>To</th><th>Status</th><th>Rent</th><th>Actions</th></tr></thead>
      <tbody>{result?.content?.map((transfer) => <tr key={transfer.transferId}><td>{transfer.transferId}</td><td>{transfer.equipment?.map((item) => item.equipmentCode).join(', ')}</td><td>{location(transfer.fromType, transfer.fromWorksiteId)}</td><td>{location(transfer.toType, transfer.toWorksiteId)}</td><td>{transfer.status}</td><td>{transfer.rentTotal != null ? `${transfer.rentalDays} days x ${Number(transfer.unitPrice).toFixed(2)} = ${Number(transfer.rentTotal).toFixed(2)}` : '-'}</td><td><button className="button button-secondary" disabled={deleting !== null} onClick={() => remove(transfer)} aria-label={`Delete transfer ${transfer.transferId}`}>{deleting === transfer.transferId ? 'Deleting...' : 'Delete'}</button></td></tr>)}</tbody>
    </table>{result?.content?.length === 0 ? <p className="state-message">No transfers found.</p> : null}</div>}
    <div className="pagination"><span>Page {page + 1} of {Math.max(1, result?.totalPages || 0)}</span><div><button className="button button-secondary" disabled={loading || deleting !== null || page === 0} onClick={() => setPage(page - 1)}>Previous</button><button className="button button-secondary" disabled={loading || deleting !== null || page + 1 >= (result?.totalPages || 0)} onClick={() => setPage(page + 1)}>Next</button></div></div>
  </section>
}
