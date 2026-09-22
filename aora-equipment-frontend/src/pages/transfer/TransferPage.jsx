import { useAuth0 } from '@auth0/auth0-react'
import { useEffect, useRef, useState } from 'react'
import { CheckCircle2 } from 'lucide-react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { completeTransfer, createTransfer, fetchCurrentUser, fetchTransfers, fetchWorksites } from '../../api/transferApi'
import './TransferPage.css'
import StoreReceiveForm from './StoreReceiveForm'
import RecordName from '../../components/RecordName'
import TransferList from './TransferList'

const conditions = ['GOOD', 'FAIR', 'DAMAGED']

const initialSendForm = {
  fromType: 'WORKSITE',
  fromWorksiteId: '',
  toType: 'WORKSITE',
  toWorksiteId: '',
  equipmentCode: '',
  condition: 'GOOD',
  deliveryPersonName: '',
  deliveryPersonPhone: '',
  notes: '',
}

function locationLabel(type, worksiteId, worksites) {
  return type === 'STORE' ? 'Store' : <RecordName id={worksiteId} name={worksites.find((site) => site.worksiteId === worksiteId)?.name} />
}

function TransferSuccessDialog({ success, onClose }) {
  const dialogRef = useRef(null)

  useEffect(() => {
    const dialog = dialogRef.current
    const previousFocus = document.activeElement
    dialog.showModal()
    return () => {
      dialog.close()
      if (previousFocus?.isConnected) previousFocus.focus()
    }
  }, [])

  return (
    <dialog ref={dialogRef} className="transfer-success-dialog" aria-labelledby="transfer-success-title" aria-describedby="transfer-success-description" onCancel={(event) => { event.preventDefault(); onClose() }}>
      <CheckCircle2 className="transfer-success-icon" size={56} aria-hidden="true" />
      <h2 id="transfer-success-title">{success.title}</h2>
      <p id="transfer-success-description">{success.description}</p>
      <button autoFocus type="button" className="button button-primary" onClick={onClose}>Done</button>
    </dialog>
  )
}

export default function TransferPage() {
  const { getAccessTokenSilently, user, logout } = useAuth0()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const equipmentCode = searchParams.get('equipmentCode') || ''
  const [mode, setMode] = useState(() => ['receive', 'all'].includes(searchParams.get('mode')) ? searchParams.get('mode') : 'send')
  const [sendForm, setSendForm] = useState(initialSendForm)
  const [worksites, setWorksites] = useState([])
  const [profile, setProfile] = useState(null)
  const [transfers, setTransfers] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [success, setSuccess] = useState(null)
  const [receivingId, setReceivingId] = useState(null)
  const [receivePrices, setReceivePrices] = useState({})
  const [receiveConditions, setReceiveConditions] = useState({})
  const [siteAction, setSiteAction] = useState(null)
  const [checkAttempt, setCheckAttempt] = useState(0)
  const isSiteAdmin = profile?.role === 'SITE_ADMIN'
  const siteActionReady = siteAction?.equipmentCode === equipmentCode && siteAction?.worksiteId === profile?.currentWorksite?.worksiteId
  const activeMode = isSiteAdmin ? siteAction?.mode : mode

  useEffect(() => {
    let cancelled = false
    fetchCurrentUser({ getAccessTokenSilently })
      .then((currentUser) => {
        if (cancelled) return
        setProfile(currentUser)
        setSendForm((current) => ({
          ...current,
          equipmentCode,
          ...(currentUser.role === 'SITE_ADMIN' ? {
            fromType: 'WORKSITE',
            fromWorksiteId: String(currentUser.currentWorksite?.worksiteId || ''),
          } : currentUser.role === 'MANAGER' || currentUser.role === 'STORE_MANAGER' ? {
            fromType: 'STORE',
            fromWorksiteId: '',
          } : {}),
        }))
      })
      .catch((requestError) => { if (!cancelled) setError(requestError.message || 'Unable to load your user profile.') })
    return () => { cancelled = true }
  }, [equipmentCode, getAccessTokenSilently])

  useEffect(() => {
    async function loadWorksites() {
      try {
        const result = await fetchWorksites({ getAccessTokenSilently })
        setWorksites(result.content ?? [])
      } catch (requestError) {
        setError(requestError.message || 'Unable to load worksites.')
      }
    }

    loadWorksites()
  }, [getAccessTokenSilently])

  useEffect(() => {
    if (!profile || profile.role !== 'SITE_ADMIN') return
    let cancelled = false
    async function selectSiteAction() {
      const worksiteId = profile.currentWorksite?.worksiteId
      if (!worksiteId) return
      try {
        const incoming = []
        let page = 0
        let result
        do {
          result = await fetchTransfers({
            getAccessTokenSilently,
            status: 'IN_TRANSIT',
            equipmentCode,
            toWorksiteId: worksiteId,
            page,
          })
          if (cancelled) return
          incoming.push(...(result.content ?? []))
          page += 1
        } while (page < result.totalPages)
        if (cancelled) return
        setTransfers(incoming)
        setSiteAction({ equipmentCode, worksiteId, mode: !equipmentCode || incoming.length > 0 ? 'receive' : 'send' })
      } catch (requestError) {
        if (!cancelled) setError(requestError.message || 'Unable to check incoming transfers. Please try again.')
      }
    }
    selectSiteAction()
    return () => { cancelled = true }
  }, [equipmentCode, getAccessTokenSilently, profile, checkAttempt])

  useEffect(() => {
    if (!profile || profile.role === 'SITE_ADMIN' || mode !== 'receive') return

    let cancelled = false
    async function loadTransfers() {
      setLoading(true)
      setError('')
      try {
        const incoming = []
        let page = 0
        let result
        do {
          result = await fetchTransfers({ getAccessTokenSilently, status: 'IN_TRANSIT', equipmentCode, page })
          if (cancelled) return
          incoming.push(...(result.content ?? []).filter((transfer) => transfer.toType === 'STORE'))
          page += 1
        } while (page < result.totalPages)
        if (!cancelled) setTransfers(incoming)
      } catch (requestError) {
        if (!cancelled) setError(requestError.message || 'Unable to load transfers.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadTransfers()
    return () => { cancelled = true }
  }, [equipmentCode, getAccessTokenSilently, mode, profile, checkAttempt])

  const updateSendForm = (field) => (event) => {
    setSendForm((current) => ({ ...current, [field]: event.target.value }))
  }

  const submitSend = async (event) => {
    event.preventDefault()
    if (!profile || (isSiteAdmin && (!siteActionReady || activeMode !== 'send'))) return
    setLoading(true)
    setError('')
    setMessage('')

    const transfer = {
      from: { type: sendForm.fromType, worksiteId: sendForm.fromType === 'WORKSITE' ? Number(sendForm.fromWorksiteId) : null },
      to: { type: sendForm.toType, worksiteId: sendForm.toType === 'WORKSITE' ? Number(sendForm.toWorksiteId) : null },
      deliveryPerson: { name: sendForm.deliveryPersonName.trim(), phone: sendForm.deliveryPersonPhone.trim() || null },
      equipment: { equipmentCode: sendForm.equipmentCode.trim(), condition: sendForm.condition },
      notes: sendForm.notes.trim() || null,
    }

    try {
      await createTransfer({ getAccessTokenSilently, transfer })
      setSendForm({ ...initialSendForm, equipmentCode, ...(isSiteAdmin ? { fromWorksiteId: String(profile.currentWorksite.worksiteId) } : { fromType: 'STORE' }) })
      setMessage('Equipment sent. The transfer is now in transit.')
      setSuccess({ title: 'Equipment successfully sent!', description: `${transfer.equipment.equipmentCode} is now in transit and awaiting confirmation at the destination.` })
    } catch (requestError) {
      setError(requestError.message || 'Unable to create transfer.')
    } finally {
      setLoading(false)
    }
  }

  const confirmReceive = async (transfer) => {
    const equipmentCode = transfer.equipment?.[0]?.equipmentCode
    const conditionAfter = receiveConditions[transfer.transferId]
    setReceivingId(transfer.transferId)
    setError('')
    setMessage('')

    try {
      const received = await completeTransfer({ getAccessTokenSilently, transferId: transfer.transferId, equipmentCode, conditionAfter, unitPrice: transfer.toType === 'STORE' ? Number(receivePrices[transfer.transferId]) : undefined })
      setTransfers((current) => current.filter((item) => item.transferId !== transfer.transferId))
      setMessage(`Transfer ${transfer.transferId} received and completed.`)
      setSuccess({ title: 'Receipt successfully confirmed!', description: `${equipmentCode} has been received. Transfer ${transfer.transferId} is now completed.${received.rentTotal != null ? ` Rent: ${received.rentalDays} days x ${Number(received.unitPrice).toFixed(2)} = ${Number(received.rentTotal).toFixed(2)}.` : ''}` })
    } catch (requestError) {
      setError(requestError.message || 'Unable to confirm receipt.')
    } finally {
      setReceivingId(null)
    }
  }

  return (
    <div className="dashboard-shell">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">AORA EQUIPMENT</p>
          <h1>Equipment transfers</h1>
          <p className="muted">Signed in as {user?.name || user?.email}</p>
        </div>
        <div className="header-actions">
          <button className="button button-secondary" onClick={() => navigate('/dashboard')}>Dashboard</button>
          <button className="button button-secondary" onClick={() => navigate('/equipment')}>Equipment</button>
          <button className="button button-secondary" onClick={() => logout({ logoutParams: { returnTo: window.location.origin } })}>Sign out</button>
        </div>
      </header>

      <main className="dashboard-content transfer-content">
        {profile && !isSiteAdmin ? <div className="transfer-switcher" role="tablist" aria-label="Transfer action">
          <button className={mode === 'send' ? 'active' : ''} onClick={() => { setMode('send'); setError(''); setMessage('') }} role="tab" aria-selected={mode === 'send'}>Send equipment</button>
          <button className={mode === 'receive' ? 'active' : ''} onClick={() => { setMode('receive'); setError(''); setMessage('') }} role="tab" aria-selected={mode === 'receive'}>Receive equipment</button>
          {profile.role === 'MANAGER' ? <button className={mode === 'all' ? 'active' : ''} onClick={() => { setMode('all'); setError(''); setMessage('') }} role="tab" aria-selected={mode === 'all'}>All transfers</button> : null}
        </div> : null}
        {isSiteAdmin ? <div className="form-message"><p className="entry-context">Your worksite: {profile.currentWorksite?.name || 'Not assigned'}{equipmentCode ? ` · Equipment: ${equipmentCode}` : ''}</p><button className="button button-secondary" onClick={() => navigate('/transfers')}>Choose equipment</button></div> : null}

        {error ? <p className="state-message error-message form-message">{error}</p> : null}
        {message ? <p className="state-message success-message form-message">{message}</p> : null}

        {!profile ? <p className="state-message" role="status">{error ? 'Your profile could not be loaded. Refresh to try again.' : 'Loading your profile...'}</p> : isSiteAdmin && !profile.currentWorksite?.worksiteId ? (
          <p className="state-message" role="status">You need an assigned worksite before starting a transfer. Contact your manager.</p>
        ) : isSiteAdmin && !siteActionReady ? (
          <div className="state-message" role="status">{error ? <><p>Could not determine the transfer action.</p><button className="button button-secondary" onClick={() => { setError(''); setCheckAttempt((value) => value + 1) }}>Try again</button></> : 'Checking incoming transfers...'}</div>
        ) : activeMode === 'all' && profile.role === 'MANAGER' ? (
          <TransferList equipmentCode={equipmentCode} />
        ) : activeMode === 'send' ? (
          <section className="equipment-panel transfer-panel">
            <div className="panel-heading"><div><h2>Send equipment</h2><p className="muted">Record an equipment movement and notify the destination.</p></div></div>
            <form className="transfer-form" onSubmit={submitSend}>
              <div className="form-section"><h3>Movement</h3><div className="form-grid">
                <label><span>From</span><select disabled={['SITE_ADMIN', 'MANAGER', 'STORE_MANAGER'].includes(profile?.role)} value={sendForm.fromType} onChange={updateSendForm('fromType')}><option value="WORKSITE">Worksite</option><option value="STORE">Store</option></select></label>
                {sendForm.fromType === 'WORKSITE' ? <label><span>From worksite</span><select required disabled={profile?.role === 'SITE_ADMIN'} value={sendForm.fromWorksiteId} onChange={updateSendForm('fromWorksiteId')}><option value="">Choose worksite</option>{worksites.map((worksite) => <option key={worksite.worksiteId} value={worksite.worksiteId}>{worksite.name}</option>)}</select></label> : null}
                <label><span>To</span><select value={sendForm.toType} onChange={updateSendForm('toType')}><option value="WORKSITE">Worksite</option><option value="STORE">Store</option></select></label>
                {sendForm.toType === 'WORKSITE' ? <label><span>To worksite</span><select required value={sendForm.toWorksiteId} onChange={updateSendForm('toWorksiteId')}><option value="">Choose worksite</option>{worksites.map((worksite) => <option key={worksite.worksiteId} value={worksite.worksiteId}>{worksite.name}</option>)}</select></label> : null}
              </div></div>
              <div className="form-section"><h3>Equipment</h3><div className="form-grid">
                <label><span>Equipment code</span><input required readOnly={Boolean(equipmentCode)} value={sendForm.equipmentCode} onChange={updateSendForm('equipmentCode')} placeholder="e.g. EXC-001" /></label>
                <label><span>Condition before sending</span><select value={sendForm.condition} onChange={updateSendForm('condition')}>{conditions.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
              </div></div>
              <div className="form-section"><h3>Delivery contact</h3><div className="form-grid">
                <label><span>Full name</span><input required value={sendForm.deliveryPersonName} onChange={updateSendForm('deliveryPersonName')} /></label>
                <label><span>Phone <small>(optional)</small></span><input value={sendForm.deliveryPersonPhone} onChange={updateSendForm('deliveryPersonPhone')} /></label>
                <label className="full-width"><span>Notes <small>(optional)</small></span><textarea value={sendForm.notes} onChange={updateSendForm('notes')} rows="3" /></label>
              </div></div>
              <div className="form-actions"><button className="button button-primary" disabled={loading}>{loading ? 'Sending...' : 'Send equipment'}</button></div>
            </form>
          </section>
        ) : (
          <>
          {['MANAGER', 'STORE_MANAGER'].includes(profile.role) ? <StoreReceiveForm key={equipmentCode} equipmentCode={equipmentCode} worksites={worksites} onReceived={(transfer) => {
            setTransfers((current) => current.filter((item) => item.transferId !== transfer.transferId))
            setMessage(`Transfer ${transfer.transferId} received and completed.`)
            setSuccess({ title: 'Receipt successfully confirmed!', description: `${transfer.equipment?.[0]?.equipmentCode} has been received at the store. Transfer ${transfer.transferId} is completed. Rent: ${transfer.rentalDays} days x ${Number(transfer.unitPrice).toFixed(2)} = ${Number(transfer.rentTotal).toFixed(2)}.` })
            setCheckAttempt((value) => value + 1)
          }} /> : null}
          <section className="equipment-panel transfer-panel">
            <div className="panel-heading"><div><h2>{isSiteAdmin && !equipmentCode ? 'Incoming transfers' : 'Receive equipment'}</h2><p className="muted">Review incoming equipment, record its condition, and confirm delivery.</p></div>{loading ? <span className="muted">Loading...</span> : null}</div>
            {transfers.length === 0 && !loading ? <p className="state-message">There are no equipment transfers waiting for receipt.</p> : null}
            <div className="receive-list">{transfers.map((transfer) => {
              const item = transfer.equipment?.[0]
              return <article className="receive-item" key={transfer.transferId}>
                <div><p className="eyebrow">TRANSFER {transfer.transferId}</p><h3>{item?.equipmentCode || 'Unknown equipment'}</h3><p className="muted">{locationLabel(transfer.fromType, transfer.fromWorksiteId, worksites)} to {locationLabel(transfer.toType, transfer.toWorksiteId, worksites)} · Sent by {transfer.deliveryPersonName}</p></div>
                <div className="receive-controls">{transfer.toType === 'STORE' ? <label><span>Daily unit price</span><input type="number" min="0" max="999999999999.99" step="0.01" value={receivePrices[transfer.transferId] ?? ''} onChange={(event) => setReceivePrices((current) => ({ ...current, [transfer.transferId]: event.target.value }))} /><small>Each started 24-hour period counts (minimum 1 day).</small></label> : null}<label><span>Condition on receipt</span><select value={receiveConditions[transfer.transferId] || ''} onChange={(event) => setReceiveConditions((current) => ({ ...current, [transfer.transferId]: event.target.value }))}><option value="">Choose condition</option>{conditions.map((condition) => <option key={condition} value={condition}>{condition}</option>)}</select></label><button className="button button-primary" disabled={!receiveConditions[transfer.transferId] || receivingId !== null || (transfer.toType === 'STORE' && (receivePrices[transfer.transferId] == null || receivePrices[transfer.transferId] === '' || !Number.isFinite(Number(receivePrices[transfer.transferId])) || Number(receivePrices[transfer.transferId]) < 0))} onClick={() => confirmReceive(transfer)}>{receivingId === transfer.transferId ? 'Confirming...' : 'Confirm receipt'}</button></div>
              </article>
            })}</div>
          </section>
          </>
        )}
      </main>
      {success ? <TransferSuccessDialog success={success} onClose={() => setSuccess(null)} /> : null}
    </div>
  )
}
