import CreationSuccess from '../../components/CreationSuccess'
import { useAuth0 } from '@auth0/auth0-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import QRCode from 'qrcode'
import { fetchEquipment, fetchEquipmentTypes, createEquipmentType, createEquipment, retireEquipment, startMaintenance, completeMaintenance } from '../../api/equipmentApi'
import { fetchCurrentUser } from '../../api/transferApi'

const pageSize = 20
const initialForm = { equipmentTypeName: '', brand: '', model: '', serialNumber: '', condition: 'GOOD', purchaseDate: '' }

export default function EquipmentPage() {
  const { getAccessTokenSilently, user, logout } = useAuth0()
  const navigate = useNavigate()
  const [equipment, setEquipment] = useState([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [search, setSearch] = useState('')
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedStatus = searchParams.get('status') || ''
  const status = ['ON_STORE', 'TRANSFER', 'ON_SITE', 'UNDER_MAINTENANCE', 'RETIRED'].includes(requestedStatus) ? requestedStatus : ''
  const setStatus = (value) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set('status', value)
      else next.delete('status')
      return next
    })
  }
  const [condition, setCondition] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [profile, setProfile] = useState(null)
  const [myWorksiteOnly, setMyWorksiteOnly] = useState(false)
  const currentWorksiteId = myWorksiteOnly ? profile?.currentWorksite?.worksiteId : undefined
  const [equipmentTypes, setEquipmentTypes] = useState([])
  const [showCreate, setShowCreate] = useState(false)
  const [createForm, setCreateForm] = useState(initialForm)
  const [createLoading, setCreateLoading] = useState(false)
  const [createError, setCreateError] = useState('')
  const [createdEquipment, setCreatedEquipment] = useState(null)
  const [qrDataUrl, setQrDataUrl] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [action, setAction] = useState(null)
  const [reason, setReason] = useState('')
  const [description, setDescription] = useState('')
  const [actionError, setActionError] = useState('')
  const [actionLoading, setActionLoading] = useState(false)
  const [success, setSuccess] = useState('')
  const submittingAction = useRef(false)
  const isManager = profile?.role === 'MANAGER'

  const canCreateEquipment = profile?.role === 'MANAGER' || profile?.role === 'STORE_MANAGER'
  const canTransferEquipment = profile?.role === 'MANAGER' || profile?.role === 'STORE_MANAGER'

  useEffect(() => {
    let cancelled = false
    Promise.all([
      fetchCurrentUser({ getAccessTokenSilently }),
      fetchEquipmentTypes({ getAccessTokenSilently }),
    ]).then(([currentUser, typeResult]) => {
      if (cancelled) return
      setProfile(currentUser)
      setEquipmentTypes(typeResult.content ?? [])
    }).catch((requestError) => {
      if (!cancelled) setError(requestError.message || 'Unable to load equipment setup data.')
    })
    return () => { cancelled = true }
  }, [getAccessTokenSilently])

  useEffect(() => {
    let cancelled = false

    async function loadEquipment() {
      setLoading(true)
      setError('')

      try {
        const result = await fetchEquipment({
          getAccessTokenSilently,
          page,
          size: pageSize,
          search,
          status,
          condition,
          currentWorksiteId,
        })

        if (!cancelled) {
          setEquipment(result.content ?? [])
          setTotalPages(result.totalPages ?? 0)
          setTotalElements(result.totalElements ?? 0)
          if (page > 0 && page >= (result.totalPages || 1)) setPage(Math.max(0, (result.totalPages || 1) - 1))
        }
      } catch (requestError) {
        if (!cancelled) {
          setError(requestError.message || 'Unable to load equipment.')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadEquipment()
    return () => {
      cancelled = true
    }
  }, [condition, getAccessTokenSilently, page, search, status, currentWorksiteId, refresh])

  const openAction = (kind, item) => {
    setAction({ kind, item })
    setReason('')
    setDescription('')
    setActionError('')
    setSuccess('')
  }

  const submitAction = async (event) => {
    event.preventDefault()
    if (submittingAction.current || !action) return
    if (action.kind === 'return' ? !canTransferEquipment : !isManager) return
    if (action.kind === 'maintenance' && !reason.trim()) {
      setActionError('Enter a reason for maintenance.')
      return
    }
    submittingAction.current = true
    setActionLoading(true)
    setActionError('')
    try {
      const equipmentCode = action.item.equipmentCode
      if (action.kind === 'maintenance') {
        await startMaintenance({ getAccessTokenSilently, equipmentCode, reason: reason.trim(), description: description.trim() || null })
        setSuccess(`${equipmentCode} is now under maintenance.`)
      } else if (action.kind === 'return') {
        await completeMaintenance({ getAccessTokenSilently, equipmentCode })
        setSuccess(`${equipmentCode} has been returned to store.`)
      } else {
        await retireEquipment({ getAccessTokenSilently, equipmentCode })
        setSuccess(`${equipmentCode} has been retired.`)
      }
      setAction(null)
      setRefresh((value) => value + 1)
    } catch (failure) {
      setActionError(failure.message || 'Unable to update equipment.')
    } finally {
      submittingAction.current = false
      setActionLoading(false)
    }
  }

  const updateFilter = (setter) => (event) => {
    setter(event.target.value)
    setPage(0)
  }

  const updateCreateForm = (field) => (event) => {
    setCreateForm((current) => ({ ...current, [field]: event.target.value }))
  }

  const closeCreate = () => {
    if (createLoading) return
    setShowCreate(false)
    setCreateError('')
    setCreatedEquipment(null)
    setQrDataUrl('')
    setCreateForm(initialForm)
  }

  const submitCreate = async (event) => {
    event.preventDefault()
    if (createLoading) return
    if (!createForm.equipmentTypeName.trim()) {
      setCreateError('Enter or select an equipment type.')
      return
    }
    setCreateLoading(true)
    setCreateError('')

    try {
      const typeName = createForm.equipmentTypeName.trim()
      let equipmentType = equipmentTypes.find((type) => type.name.toLowerCase() === typeName.toLowerCase())
      if (!equipmentType) {
        equipmentType = await createEquipmentType({ getAccessTokenSilently, name: typeName })
        setEquipmentTypes((current) => [...current.filter((type) => type.equipmentTypeId !== equipmentType.equipmentTypeId), equipmentType])
      }
      const created = await createEquipment({
        getAccessTokenSilently,
        equipment: {
          equipmentTypeId: equipmentType.equipmentTypeId,
          brand: createForm.brand.trim(),
          model: createForm.model.trim() || null,
          serialNumber: createForm.serialNumber.trim(),
          condition: createForm.condition,
          purchaseDate: createForm.purchaseDate,
        },
      })
      const qr = await QRCode.toDataURL(created.equipmentCode, { width: 320, margin: 2 })
      setCreatedEquipment(created)
      setQrDataUrl(qr)
      setEquipment((current) => [created, ...current])
      setTotalElements((current) => current + 1)
    } catch (requestError) {
      setCreateError(requestError.message || 'Unable to add equipment.')
    } finally {
      setCreateLoading(false)
    }
  }

  return (
    <div className="dashboard-shell">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">AORA EQUIPMENT</p>
          <h1>Equipment overview</h1>
          <p className="muted">Signed in as {user?.name || user?.email}</p>
        </div>
        <div className="header-actions"><button className="button button-secondary" onClick={() => navigate('/dashboard')}>Dashboard</button><button className="button button-secondary" onClick={() => navigate('/transfers')}>Transfers</button><button onClick={() => logout({ logoutParams: { returnTo: window.location.origin } })} className="button button-secondary">Sign out</button></div>
      </header>

      <main className="dashboard-content">
        {success ? <CreationSuccess title={success} /> : null}
        {profile?.role === 'SITE_ADMIN' ? <section aria-label="Equipment scope">
          <div className="transfer-switcher" role="group" aria-label="Show equipment from">
            <button className={!myWorksiteOnly ? 'active' : ''} aria-pressed={!myWorksiteOnly} onClick={() => { setMyWorksiteOnly(false); setPage(0) }}>All equipment</button>
            <button className={myWorksiteOnly ? 'active' : ''} aria-pressed={myWorksiteOnly} disabled={!profile.currentWorksite?.worksiteId} onClick={() => { setMyWorksiteOnly(true); setPage(0) }}>My worksite equipment</button>
          </div>
          <p className="muted">{myWorksiteOnly ? `Showing equipment at ${profile.currentWorksite.name}.` : 'Showing equipment across all worksites and stores.'}{!profile.currentWorksite?.worksiteId ? ' You have no assigned worksite.' : ''}</p>
        </section> : null}
        <section className="toolbar" aria-label="Equipment filters">
          <label className="search-field">
            <span>Search</span>
            <input value={search} onChange={updateFilter(setSearch)} placeholder="Code, brand, model or serial" />
          </label>
          <label>
            <span>Status</span>
            <select value={status} onChange={updateFilter(setStatus)}>
              <option value="">All statuses</option>
              <option value="ON_STORE">On store</option>
              <option value="TRANSFER">Transfer</option>
              <option value="ON_SITE">On site</option>
              <option value="UNDER_MAINTENANCE">Under maintenance</option>
              <option value="RETIRED">Retired</option>
            </select>
          </label>
          <label>
            <span>Condition</span>
            <select value={condition} onChange={updateFilter(setCondition)}>
              <option value="">All conditions</option>
              <option value="GOOD">Good</option>
              <option value="FAIR">Fair</option>
              <option value="DAMAGED">Damaged</option>
            </select>
          </label>
        </section>

        <section className="equipment-panel">
          <div className="panel-heading">
            <div>
              <h2>Equipment</h2>
              <p className="muted">{totalElements} total records</p>
            </div>
            <div className="panel-actions">{loading ? <span className="muted">Loading...</span> : null}{canCreateEquipment ? <button className="button button-primary" onClick={() => { setShowCreate(true); setCreateError('') }}>Add equipment</button> : null}</div>
          </div>

          {error ? <p className="state-message error-message">{error}</p> : null}
          {!loading && !error && equipment.length === 0 ? <p className="state-message">No equipment matches these filters.</p> : null}
          {!loading && !error && equipment.length > 0 ? (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>Equipment</th><th>Type</th><th>Brand / model</th><th>Status</th><th>Condition</th><th>Worksite</th>{canTransferEquipment ? <th>Actions</th> : null}</tr>
                </thead>
                <tbody>
                  {equipment.map((item) => (
                    <tr
                      key={item.equipmentId}
                      className="clickable-row"
                      tabIndex="0"
                      role="button"
                      aria-label={`View transaction history for ${item.equipmentCode}`}
                      onClick={() => navigate(`/equipment/${encodeURIComponent(item.equipmentCode)}/history`)}
                      onKeyDown={(event) => {
                        if (event.target !== event.currentTarget) return
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          navigate(`/equipment/${encodeURIComponent(item.equipmentCode)}/history`)
                        }
                      }}
                    >
                      <td><strong>{item.equipmentCode}</strong><small>{item.serialNumber}</small></td>
                      <td>{item.equipmentType?.name || '-'}</td>
                      <td>{item.brand} / {item.model}</td>
                      <td><span className="status-badge">{item.status}</span></td>
                      <td>{item.condition}</td>
                      <td>{item.currentWorksite?.name || 'Unassigned'}</td>
                      {canTransferEquipment ? <td onClick={(event) => event.stopPropagation()}>
                        {item.status === 'ON_STORE' ? <div className="panel-actions">
                          <button className="button button-primary" aria-label={`Send equipment ${item.equipmentCode}`} onClick={() => navigate(`/transfers/manage?equipmentCode=${encodeURIComponent(item.equipmentCode)}&mode=send`)}>Send equipment</button>
                          {isManager ? <><button className="button button-secondary" onClick={() => openAction('maintenance', item)}>Send for maintenance</button><button className="button button-secondary" onClick={() => openAction('retire', item)}>Retire</button></> : null}
                        </div> : item.status === 'UNDER_MAINTENANCE' ? <button className="button button-primary" onClick={() => openAction('return', item)}>Return to store</button> : <small className="muted">{item.status === 'RETIRED' ? 'Retired' : item.status === 'UNDER_MAINTENANCE' ? 'Maintenance in progress' : item.status === 'TRANSFER' ? 'Transfer in progress' : 'Equipment must be in store to send.'}</small>}
                      </td> : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}

          <div className="pagination">
            <span className="muted">Page {page + 1} of {Math.max(totalPages, 1)}</span>
            <div>
              <button className="button button-secondary" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>Previous</button>
              <button className="button button-primary" disabled={page + 1 >= totalPages || loading} onClick={() => setPage(page + 1)}>Next</button>
            </div>
          </div>
        </section>
      </main>
      {action ? <div className="modal-backdrop">
        <section className="create-modal" role="dialog" aria-modal="true" aria-labelledby="equipment-action-title" onKeyDown={(event) => { if (event.key === 'Escape' && !submittingAction.current) setAction(null) }}>
          <h2 id="equipment-action-title">{action.kind === 'maintenance' ? 'Send for maintenance' : action.kind === 'return' ? 'Return to store' : 'Retire equipment'}</h2>
          <p><strong>{action.item.equipmentCode}</strong> — {action.item.brand} {action.item.model}</p>
          <form className="create-form" onSubmit={submitAction}>
            {actionError ? <p className="error-message" role="alert">{actionError}</p> : null}
            {action.kind === 'maintenance' ? <><p className="muted">This starts maintenance and removes the equipment from available stock.</p><label><span>Reason</span><input autoFocus required maxLength={255} disabled={actionLoading} value={reason} onChange={(event) => setReason(event.target.value)} /></label><label><span>Description (optional)</span><input maxLength={255} disabled={actionLoading} value={description} onChange={(event) => setDescription(event.target.value)} /></label></> : action.kind === 'return' ? <p>This completes maintenance and returns the equipment to available store stock.</p> : <p>This will retire the equipment and make it unavailable for future transfers. This action cannot be undone here.</p>}
            <div className="modal-actions"><button autoFocus={action.kind === 'retire'} type="button" className="button button-secondary" disabled={actionLoading} onClick={() => setAction(null)}>Cancel</button><button className="button button-primary" disabled={actionLoading}>{actionLoading ? 'Saving...' : action.kind === 'maintenance' ? 'Start maintenance' : action.kind === 'return' ? 'Return to store' : 'Retire equipment'}</button></div>
          </form>
        </section>
      </div> : null}
      {showCreate ? (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) closeCreate() }}>
          <section className="create-modal" role="dialog" aria-modal="true" aria-labelledby="add-equipment-title">
            {!createdEquipment ? <>
              <div className="modal-heading"><div><p className="eyebrow">INVENTORY</p><h2 id="add-equipment-title">Add equipment</h2></div><button className="icon-button" aria-label="Close" onClick={closeCreate}>×</button></div>
              {createError ? <p className="error-message">{createError}</p> : null}
              <form className="create-form" onSubmit={submitCreate}>
                <label><span>Equipment Name</span><input required maxLength={255} list="equipment-type-options" autoComplete="off" disabled={createLoading} value={createForm.equipmentTypeName} onChange={updateCreateForm('equipmentTypeName')} placeholder="Type to search or add a new type" aria-describedby="equipment-type-help" /><datalist id="equipment-type-options">{equipmentTypes.map((type) => <option key={type.equipmentTypeId} value={type.name} />)}</datalist><small id="equipment-type-help" className="muted">{createForm.equipmentTypeName.trim() && !equipmentTypes.some((type) => type.name.toLowerCase() === createForm.equipmentTypeName.trim().toLowerCase()) ? `A new type ?${createForm.equipmentTypeName.trim()}? will be created when you save the equipment. You can also pick a matching suggestion.` : 'Type to find an existing type, or enter a new name to create it automatically.'}</small></label>
                <div className="form-grid"><label><span>Brand</span><input required value={createForm.brand} onChange={updateCreateForm('brand')} /></label><label><span>Model <small>(optional)</small></span><input value={createForm.model} onChange={updateCreateForm('model')} /></label><label><span>Serial number</span><input required value={createForm.serialNumber} onChange={updateCreateForm('serialNumber')} /></label><label><span>Condition</span><select value={createForm.condition} onChange={updateCreateForm('condition')}>{['GOOD', 'FAIR', 'DAMAGED'].map((condition) => <option key={condition} value={condition}>{condition}</option>)}</select></label><label><span>Purchase date</span><input required type="date" max={new Date().toISOString().slice(0, 10)} value={createForm.purchaseDate} onChange={updateCreateForm('purchaseDate')} /></label></div>
                <div className="modal-actions"><button type="button" className="button button-secondary" onClick={closeCreate}>Cancel</button><button className="button button-primary" disabled={createLoading}>{createLoading ? 'Creating...' : 'Create equipment'}</button></div>
              </form>
            </> : <div className="qr-result"><CreationSuccess title="Equipment created successfully!">{createdEquipment.equipmentCode} has been added to your inventory. Download its QR code below.</CreationSuccess><div className="modal-heading"><div><p className="eyebrow">EQUIPMENT CREATED</p><h2>Code: {createdEquipment.equipmentCode}</h2></div><button className="icon-button" aria-label="Close" onClick={closeCreate}>×</button></div><img src={qrDataUrl} alt={`QR code for ${createdEquipment.equipmentCode}`} /><p className="muted">Download this QR code and attach it to the equipment.</p><div className="modal-actions"><a className="button button-primary" href={qrDataUrl} download={`${createdEquipment.equipmentCode}-qr.png`}>Download QR code</a><button className="button button-secondary" onClick={closeCreate}>Done</button></div></div>}
          </section>
        </div>
      ) : null}
    </div>
  )
}
