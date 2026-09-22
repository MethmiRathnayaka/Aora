import CreationSuccess from '../../components/CreationSuccess'
import { useEffect, useRef, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'
import { createWorksite, fetchCurrentUser } from '../../api/transferApi'

const initialForm = { name: '', projectCode: '', address: '' }

export default function CreateWorksitePage() {
  const { getAccessTokenSilently } = useAuth0()
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [profileError, setProfileError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [form, setForm] = useState(initialForm)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState(null)
  const submitting = useRef(false)

  useEffect(() => {
    let cancelled = false
    fetchCurrentUser({ getAccessTokenSilently })
      .then((result) => { if (!cancelled) setProfile(result) })
      .catch((failure) => { if (!cancelled) setProfileError(failure.message || 'Unable to check permissions.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [getAccessTokenSilently, attempt])

  async function submit(event) {
    event.preventDefault()
    if (submitting.current || profile?.role !== 'MANAGER') return
    const worksite = Object.fromEntries(Object.entries(form).map(([key, value]) => [key, value.trim()]))
    if (!worksite.name || !worksite.projectCode) {
      setError('Enter a worksite name and project code.')
      return
    }
    submitting.current = true
    setSaving(true)
    setError('')
    try {
      setCreated(await createWorksite({ getAccessTokenSilently, worksite }))
      setForm(initialForm)
    } catch (failure) {
      setError(failure.message || 'Unable to create worksite.')
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  return (
    <div className="dashboard-shell">
      <header className="dashboard-header"><div><p className="eyebrow">AORA EQUIPMENT</p><h1>Create worksite</h1><p className="muted">Add an active worksite for equipment transfers and site assignments.</p></div><Link className="button button-secondary" to="/dashboard">Dashboard</Link></header>
      <main className="dashboard-content user-create-content"><section className="equipment-panel user-create-panel">
        {loading ? <p className="state-message" role="status">Checking permissions...</p> : profileError ? <div className="state-message"><p role="alert" className="error-message">{profileError}</p><button className="button button-secondary" onClick={() => { setLoading(true); setProfileError(''); setAttempt((value) => value + 1) }}>Try again</button></div> : profile?.role !== 'MANAGER' ? <p className="state-message" role="alert">Only managers can create worksites.</p> : created ? (
          <div className="create-form"><CreationSuccess title="Worksite created successfully!">{created.name} ({created.projectCode}) is now active and ready for site assignments and equipment transfers.</CreationSuccess><div className="form-actions"><Link className="button button-primary" to={`/worksites/${created.worksiteId}`}>View worksite</Link><Link className="button button-secondary" to="/users/create">Assign a new site admin</Link><Link className="button button-secondary" to="/transfers">Transfers</Link><button className="button button-primary" onClick={() => setCreated(null)}>Create another worksite</button></div></div>
        ) : (
          <form className="create-form" onSubmit={submit}>
            {error ? <p className="error-message" role="alert">{error}</p> : null}
            <label><span>Worksite name</span><input required maxLength={255} disabled={saving} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
            <label><span>Project code</span><input required maxLength={255} disabled={saving} value={form.projectCode} onChange={(event) => setForm({ ...form, projectCode: event.target.value })} /></label>
            <label><span>Address (optional)</span><input maxLength={255} disabled={saving} value={form.address} onChange={(event) => setForm({ ...form, address: event.target.value })} /></label>
            <div className="form-actions"><button className="button button-primary" disabled={saving}>{saving ? 'Creating...' : 'Create worksite'}</button></div>
          </form>
        )}
      </section></main>
    </div>
  )
}
