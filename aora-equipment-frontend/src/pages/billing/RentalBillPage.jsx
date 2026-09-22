import { useEffect, useRef, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'
import { fetchCurrentUser, fetchRentalBill, fetchWorksites } from '../../api/transferApi'
import './RentalBillPage.css'

const allowedRoles = ['MANAGER', 'STORE_MANAGER']
const money = (value) => Number(value).toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const dateTime = (value) => value ? new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Colombo', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '-'
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())

export default function RentalBillPage() {
  const { getAccessTokenSilently } = useAuth0()
  const [profile, setProfile] = useState(null)
  const [worksites, setWorksites] = useState([])
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [filters, setFilters] = useState(() => { const end = today(); return { worksiteId: '', fromDate: `${end.slice(0, 7)}-01`, toDate: end } })
  const [bill, setBill] = useState(null)
  const busy = useRef(false)
  const requestVersion = useRef(0)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError('')
      try {
        const user = await fetchCurrentUser({ getAccessTokenSilently })
        if (cancelled) return
        setProfile(user)
        if (!allowedRoles.includes(user.role)) return
        const sites = []
        let page = 0
        let response
        do {
          response = await fetchWorksites({ getAccessTokenSilently, page })
          if (cancelled) return
          sites.push(...(response.content || []))
          page += 1
        } while (page < response.totalPages)
        setWorksites(sites)
      } catch (failure) {
        if (!cancelled) setError(failure.message || 'Unable to load billing filters.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true; requestVersion.current += 1 }
  }, [getAccessTokenSilently, attempt])

  function updateFilter(field, value) {
    requestVersion.current += 1
    setFilters((current) => ({ ...current, [field]: value }))
    setBill(null)
    setError('')
  }

  async function generate(event) {
    event.preventDefault()
    if (busy.current) return
    if (filters.fromDate > filters.toDate) {
      setError('The end date must be on or after the start date.')
      return
    }
    busy.current = true
    const version = ++requestVersion.current
    setGenerating(true)
    setError('')
    setBill(null)
    try {
      const result = await fetchRentalBill({ getAccessTokenSilently, ...filters })
      if (version === requestVersion.current) setBill(result)
    } catch (failure) {
      if (version === requestVersion.current) setError(failure.message || 'Unable to generate the rental bill.')
    } finally {
      busy.current = false
      setGenerating(false)
    }
  }

  return <div className="dashboard-shell rental-billing">
    <header className="dashboard-header billing-no-print"><div><p className="eyebrow">AORA EQUIPMENT</p><h1>Rental bills</h1><p className="muted">View and print rental charges for a worksite.</p></div><Link className="button button-secondary" to="/dashboard">Dashboard</Link></header>
    <main className="dashboard-content">
      {loading ? <p role="status">Loading billing filters...</p> : profile && !allowedRoles.includes(profile.role) ? <p role="alert">Rental bills are available to managers and store managers only.</p> : <>
        <section className="equipment-panel billing-no-print">
          <form className="billing-filters" onSubmit={generate}>
            <label><span>Worksite</span><select required disabled={generating || !worksites.length} value={filters.worksiteId} onChange={(event) => updateFilter('worksiteId', event.target.value)}><option value="">Select a worksite</option>{worksites.map((site) => <option key={site.worksiteId} value={site.worksiteId}>{site.name} ({site.projectCode})</option>)}</select></label>
            <label><span>Returned from</span><input type="date" required disabled={generating} value={filters.fromDate} max={filters.toDate || undefined} onChange={(event) => updateFilter('fromDate', event.target.value)} /></label>
            <label><span>Returned through</span><input type="date" required disabled={generating} value={filters.toDate} min={filters.fromDate || undefined} onChange={(event) => updateFilter('toDate', event.target.value)} /></label>
            <button className="button button-primary" disabled={generating || !profile || !worksites.length}>{generating ? 'Generating...' : 'Generate bill'}</button>
          </form>
          <p className="muted">Includes saved rent for store returns within these dates (Sri Lanka time), charged to the returning worksite. Each line includes the full rental period, even when dispatch was before the selected dates.</p>
          {!worksites.length && !error ? <p>No worksites are available.</p> : null}
        </section>
        {bill ? <>
          <div className="billing-toolbar billing-no-print"><span>{bill.lines.length} rental charge{bill.lines.length === 1 ? '' : 's'}</span><button className="button button-primary" disabled={!bill.lines.length} onClick={() => window.print()}>Print / Save as PDF</button></div>
          <article className="equipment-panel rental-bill" aria-label="Rental bill">
            <div className="bill-heading"><div><p className="eyebrow">AORA CONSTRUCTION</p><h2>Equipment rental bill</h2></div><p>Generated<br />{dateTime(bill.generatedAt)}</p></div>
            <div className="bill-parties"><div><h3>{bill.worksiteName}</h3><p>Project: {bill.projectCode}</p>{bill.address ? <p>{bill.address}</p> : null}</div><div><strong>Store return period</strong><p>{bill.fromDate} to {bill.toDate} (inclusive)</p><p>All times: Sri Lanka</p></div></div>
            {bill.lines.length ? <div className="table-wrap"><table className="bill-table"><thead><tr><th>Return ref.</th><th>Equipment</th><th>Store dispatch</th><th>Store return</th><th className="bill-number">Days</th><th className="bill-number">Daily price</th><th className="bill-number">Amount</th></tr></thead><tbody>{bill.lines.map((line) => <tr key={line.transferId}><td>#{line.transferId}</td><td>{line.equipmentCode}</td><td>{dateTime(line.dispatchedAt)}</td><td>{dateTime(line.returnedAt)}</td><td className="bill-number">{line.days}</td><td className="bill-number">{money(line.unitPrice)}</td><td className="bill-number">{money(line.amount)}</td></tr>)}</tbody><tfoot><tr><th colSpan="6" scope="row">Total rent</th><td className="bill-number">{money(bill.total)}</td></tr></tfoot></table></div> : <p className="state-message" role="status">No rental charges found for this worksite and period.</p>}
            <p className="bill-note">Rent = daily unit price x each started 24-hour period from store dispatch to store receipt, with a minimum of one day. Only completed returns with recorded rent are included.</p>
          </article>
        </> : null}
      </>}
      {error ? <p className="error-message billing-no-print" role="alert">{error} {!profile || !worksites.length ? <button className="button button-secondary" onClick={() => setAttempt((value) => value + 1)}>Retry</button> : null}</p> : null}
    </main>
  </div>
}
