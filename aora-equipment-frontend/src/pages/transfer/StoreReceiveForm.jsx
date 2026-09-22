import { useEffect, useRef, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { completeTransfer, fetchStoreReturnEquipment, fetchTransfers, receiveAtStore } from '../../api/transferApi'
import RecordName from '../../components/RecordName'

export default function StoreReceiveForm({ equipmentCode, worksites, onReceived }) {
  const { getAccessTokenSilently } = useAuth0()
  const [code, setCode] = useState(equipmentCode)
  const [lookup, setLookup] = useState({ code: equipmentCode, attempt: 0 })
  const [result, setResult] = useState(null)
  const [checking, setChecking] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [unitPrice, setUnitPrice] = useState('')
  const [condition, setCondition] = useState('')
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [notes, setNotes] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const submitting = useRef(false)

  useEffect(() => {
    if (!lookup.code) return
    let cancelled = false
    async function check() {
      setChecking(true)
      setResult(null)
      setError('')
      setCondition('')
      setUnitPrice('')
      setConfirmed(false)
      setName('')
      setPhone('')
      setNotes('')
      try {
        const pending = await fetchTransfers({ getAccessTokenSilently, equipmentCode: lookup.code, status: 'IN_TRANSIT' })
        if (cancelled) return
        if (pending.content?.length) {
          if (pending.content.length !== 1 || pending.content[0].toType !== 'STORE') throw new Error('This equipment has an active transfer that cannot be received at the store. Resolve that transfer first.')
          setResult({ transfer: pending.content[0] })
        } else {
          const equipment = await fetchStoreReturnEquipment({ getAccessTokenSilently, equipmentCode: lookup.code })
          if (cancelled) return
          if (equipment.status !== 'ON_SITE' || !equipment.currentWorksiteId) throw new Error('This equipment is not currently available for return from a worksite. It may already be in store or in transit.')
          setResult({ equipment })
        }
      } catch (failure) {
        if (!cancelled) setError(failure.message || 'Unable to check equipment.')
      } finally {
        if (!cancelled) setChecking(false)
      }
    }
    check()
    return () => { cancelled = true }
  }, [getAccessTokenSilently, lookup])

  async function submit(event) {
    event.preventDefault()
    if (submitting.current || !result || !confirmed || !condition || unitPrice === '' || !Number.isFinite(Number(unitPrice)) || Number(unitPrice) < 0) return
    submitting.current = true
    setSaving(true)
    setError('')
    try {
      const transfer = result.transfer
        ? await completeTransfer({ getAccessTokenSilently, transferId: result.transfer.transferId, equipmentCode: lookup.code, conditionAfter: condition, unitPrice: Number(unitPrice) })
        : await receiveAtStore({ getAccessTokenSilently, receipt: { equipmentCode: lookup.code, fromWorksiteId: result.equipment.currentWorksiteId, deliveryPersonName: name.trim(), deliveryPersonPhone: phone.trim() || null, conditionAfter: condition, receiptConfirmed: confirmed, unitPrice: Number(unitPrice), notes: notes.trim() || null } })
      setResult(null)
      setCode('')
      onReceived(transfer)
    } catch (failure) {
      setError(failure.message || 'Unable to receive equipment. Check the equipment again before retrying.')
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  const sourceId = result?.equipment?.currentWorksiteId || result?.transfer?.fromWorksiteId
  const source = result?.transfer?.fromType === 'STORE' ? 'Store' : <RecordName id={sourceId} name={worksites.find((site) => site.worksiteId === sourceId)?.name} />
  return <section className="equipment-panel transfer-panel store-receive-panel">
    <div className="panel-heading"><div><h2>Receive at store by equipment code</h2><p className="muted">Check for an existing transfer or record a return from the current worksite.</p></div></div>
    <form className="create-form" onSubmit={(event) => { event.preventDefault(); setResult(null); setLookup((current) => ({ code: code.trim(), attempt: current.attempt + 1 })) }}>
      <label><span>Equipment code</span><input required disabled={saving || checking} value={code} onChange={(event) => { setCode(event.target.value); setResult(null) }} placeholder="e.g. EXC-001" /></label>
      <button className="button button-secondary" disabled={saving || checking || !code.trim()}>{checking ? 'Checking...' : 'Check equipment'}</button>
    </form>
    {error ? <p className="state-message error-message" role="alert">{error}</p> : null}
    {result ? <form className="create-form" onSubmit={submit}>
      <p className="form-message">{result.transfer ? `Existing transfer ${result.transfer.transferId}: confirm receipt to complete it.` : 'No pending transfer exists. Confirm receipt to create and complete the return.'}<br /><strong>{lookup.code}: {source} to Store</strong></p>
      {!result.transfer ? <><label><span>Delivery person name</span><input required disabled={saving} value={name} onChange={(event) => setName(event.target.value)} /></label><label><span>Delivery phone (optional)</span><input disabled={saving} value={phone} onChange={(event) => setPhone(event.target.value)} /></label><label><span>Notes (optional)</span><textarea disabled={saving} value={notes} onChange={(event) => setNotes(event.target.value)} /></label></> : <p className="muted">Delivery contact: {result.transfer.deliveryPersonName}</p>}
      <label><span>Daily unit price</span><input type="number" min="0" max="999999999999.99" step="0.01" required disabled={saving} value={unitPrice} onChange={(event) => setUnitPrice(event.target.value)} /></label>
      <p className="muted">Rent = daily unit price x days from original store dispatch to store receipt, charging each started 24-hour period (minimum 1 day). The calculated total is shown after confirmation.</p>
      <label><span>Condition on receipt</span><select required disabled={saving} value={condition} onChange={(event) => setCondition(event.target.value)}><option value="">Choose condition</option>{['GOOD', 'FAIR', 'DAMAGED'].map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
      <label><span><input type="checkbox" required disabled={saving} checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} /> I confirm this equipment has physically arrived at the store and I have checked its condition.</span></label>
      <button className="button button-primary" disabled={saving || !confirmed || !condition || (!result.transfer && !name.trim())}>{saving ? 'Receiving...' : result.transfer ? 'Confirm receipt' : 'Create return and confirm receipt'}</button>
    </form> : null}
  </section>
}
