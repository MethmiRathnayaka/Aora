import CreationSuccess from '../../components/CreationSuccess'
import { useEffect, useRef, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { useBlocker, useNavigate } from 'react-router-dom'
import { fetchCurrentUser, fetchWorksites } from '../../api/transferApi'
import { createUser } from '../../api/userApi'
import './CreateUserPage.css'

const initialForm = { firstName: '', lastName: '', email: '', phone: '', role: 'SITE_ADMIN', worksiteId: '' }
const roles = { SITE_ADMIN: 'Site admin', STORE_MANAGER: 'Store manager', MANAGER: 'Manager' }

function PasswordLeaveDialog({ onStay, onLeave }) {
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
  return <dialog ref={dialogRef} className="password-leave-dialog" aria-labelledby="password-leave-title" aria-describedby="password-leave-description" onCancel={(event) => { event.preventDefault(); onStay() }}>
    <h2 id="password-leave-title">Have you shared the password securely?</h2>
    <p id="password-leave-description">Leaving this screen will hide the temporary password permanently. You will not be able to view it again. Share it securely with the user before continuing.</p>
    <div className="form-actions"><button autoFocus type="button" className="button button-primary" onClick={onStay}>Stay and view password</button><button type="button" className="button button-secondary" onClick={onLeave}>I have shared it ? continue</button></div>
  </dialog>
}

export default function CreateUserPage() {
  const { getAccessTokenSilently } = useAuth0()
  const navigate = useNavigate()
  const submitting = useRef(false)
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [profileError, setProfileError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [form, setForm] = useState(initialForm)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [createdUser, setCreatedUser] = useState(null)
  const [showPassword, setShowPassword] = useState(false)
  const [worksites, setWorksites] = useState([])
  const [sitesLoading, setSitesLoading] = useState(true)
  const [sitesError, setSitesError] = useState('')
  const [sitesAttempt, setSitesAttempt] = useState(0)
  const [confirmAnother, setConfirmAnother] = useState(false)
  const hasTemporaryPassword = Boolean(createdUser?.temporaryPassword)
  const blocker = useBlocker(hasTemporaryPassword)

  useEffect(() => {
    if (!hasTemporaryPassword) return
    const warnBeforeUnload = (event) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warnBeforeUnload)
    return () => window.removeEventListener('beforeunload', warnBeforeUnload)
  }, [hasTemporaryPassword])

  function resetCreatedUser() {
    setConfirmAnother(false)
    setCreatedUser(null)
    setShowPassword(false)
  }


  useEffect(() => {
    if (profile?.role !== 'MANAGER') return
    let cancelled = false
    async function loadWorksites() {
      try {
        const sites = []
        let page = 0
        let result
        do {
          result = await fetchWorksites({ getAccessTokenSilently, page })
          sites.push(...result.content)
          page += 1
        } while (!cancelled && page < result.totalPages)
        if (!cancelled) setWorksites(sites.filter((site) => site.status === 'ACTIVE' && !site.siteAdminId))
      } catch (requestError) {
        if (!cancelled) setSitesError(requestError.message || 'Unable to load worksites.')
      } finally {
        if (!cancelled) setSitesLoading(false)
      }
    }
    loadWorksites()
    return () => { cancelled = true }
  }, [getAccessTokenSilently, profile, sitesAttempt])

  useEffect(() => {
    let cancelled = false
    fetchCurrentUser({ getAccessTokenSilently })
      .then((currentUser) => { if (!cancelled) setProfile(currentUser) })
      .catch((requestError) => { if (!cancelled) setProfileError(requestError.message || 'Unable to check your permissions.') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [getAccessTokenSilently, attempt])

  function updateField(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submitting.current || profile?.role !== 'MANAGER') return
    const values = Object.fromEntries(Object.entries(form).map(([key, value]) => [key, value.trim()]))
    if (!values.firstName || !values.lastName || !values.email) {
      setError('Enter a first name, last name, and email address.')
      return
    }
    if (values.role === 'SITE_ADMIN' && (sitesLoading || sitesError || !worksites.some((site) => String(site.worksiteId) === values.worksiteId))) {
      setError('Select an available worksite for the site admin.')
      return
    }
    submitting.current = true
    setSaving(true)
    setError('')
    try {
      const result = await createUser({ getAccessTokenSilently, user: { ...values, phone: values.phone || null, worksiteId: values.role === 'SITE_ADMIN' ? Number(values.worksiteId) : null } })
      setCreatedUser(result)
      if (result.currentWorksite) setWorksites((sites) => sites.filter((site) => site.worksiteId !== result.currentWorksite.worksiteId))
      setForm(initialForm)
    } catch (requestError) {
      setError(requestError.message || 'Unable to create the user. Please try again.')
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  return (
    <div className="dashboard-shell">
      <header className="dashboard-header">
        <div><p className="eyebrow">AORA EQUIPMENT</p><h1>Create user</h1><p className="muted">Add a team member and assign their role.</p></div>
        <button className="button button-secondary" disabled={saving} onClick={() => navigate('/dashboard')}>Dashboard</button>
      </header>
      <main className="dashboard-content user-create-content">
        <section className="equipment-panel user-create-panel">
          {loading ? <p className="state-message" role="status">Checking permissions...</p> : profileError ? (
            <div className="state-message"><p className="error-message" role="alert">{profileError}</p><button className="button button-secondary" onClick={() => { setLoading(true); setProfileError(''); setAttempt((value) => value + 1) }}>Try again</button></div>
          ) : profile?.role !== 'MANAGER' ? (
            <p className="state-message" role="alert">Only managers can create users. Contact your manager for access.</p>
          ) : createdUser ? (
            <div className="create-form">
              <CreationSuccess title="User created successfully!">{createdUser.firstName} {createdUser.lastName} can now sign in with their account. Their role and account details are shown below.</CreationSuccess>
              <p className="entry-context">{createdUser.email} · {roles[createdUser.role]}</p>
              {createdUser.temporaryPassword ? <>
                <label><span>Temporary password</span><input readOnly type={showPassword ? 'text' : 'password'} value={createdUser.temporaryPassword} autoComplete="off" /></label>
                <div><button type="button" className="button button-secondary" onClick={() => setShowPassword((value) => !value)}>{showPassword ? 'Hide password' : 'Show password'}</button></div>
                <div className="temporary-password-warning" role="alert"><strong>Important: this password is shown only once</strong><p>Share this password securely with the user <b>before leaving this page</b>. It will not be displayed again.</p><p>Leaving, refreshing, or creating another user will hide this password permanently.</p></div>
              </> : null}
              {createdUser.currentWorksite ? <p className="entry-context">Assigned site: {createdUser.currentWorksite.name} ({createdUser.currentWorksite.projectCode})</p> : null}
              <div className="form-actions"><button className="button button-primary" onClick={() => { if (hasTemporaryPassword) setConfirmAnother(true); else resetCreatedUser() }}>Create another user</button></div>
            </div>
          ) : <>
            <div className="panel-heading"><div><h2>User details</h2><p className="muted">A sign-in account and temporary password will be created automatically.</p></div></div>
            <form className="create-form" onSubmit={handleSubmit} aria-busy={saving}>
              <div className="form-grid">
                <label><span>First name *</span><input name="firstName" autoComplete="given-name" required value={form.firstName} onChange={updateField} disabled={saving} /></label>
                <label><span>Last name *</span><input name="lastName" autoComplete="family-name" required value={form.lastName} onChange={updateField} disabled={saving} /></label>
                <label><span>Email address *</span><input name="email" type="email" autoComplete="email" required value={form.email} onChange={updateField} disabled={saving} /></label>
                <label><span>Phone number <small>(optional)</small></span><input name="phone" type="tel" autoComplete="tel" value={form.phone} onChange={updateField} disabled={saving} /></label>
                <label className="full-width"><span>Role *</span><select name="role" required value={form.role} onChange={updateField} disabled={saving}>{Object.entries(roles).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><small>Managers can also create users and change roles.</small></label>
                {form.role === 'SITE_ADMIN' ? <div className="full-width">
                  <label><span>Worksite *</span><select name="worksiteId" required value={form.worksiteId} onChange={updateField} disabled={saving || sitesLoading || Boolean(sitesError)}>
                    <option value="">{sitesLoading ? 'Loading worksites...' : 'Select a worksite'}</option>
                    {worksites.map((site) => <option key={site.worksiteId} value={site.worksiteId}>{site.name} ({site.projectCode})</option>)}
                  </select><small>Choose an active worksite without an assigned site admin.</small></label>
                  {sitesError ? <p className="error-message" role="alert">{sitesError}</p> : null}
                  {!sitesLoading && !sitesError && worksites.length === 0 ? <p className="muted">No available worksites. Create an active worksite before adding a site admin.</p> : null}
                  {!sitesLoading ? <button type="button" className="button button-secondary" disabled={saving} onClick={() => { setSitesLoading(true); setSitesError(''); setSitesAttempt((value) => value + 1) }}>Refresh worksites</button> : null}
                </div> : null}
              </div>
              {error ? <p className="error-message" role="alert">{error}</p> : null}
              <div className="form-actions"><button type="button" className="button button-secondary" disabled={saving} onClick={() => navigate('/dashboard')}>Cancel</button><button type="submit" className="button button-primary" disabled={saving || (form.role === 'SITE_ADMIN' && (sitesLoading || Boolean(sitesError) || !form.worksiteId || worksites.length === 0))}>{saving ? 'Creating user...' : 'Create user'}</button></div>
            </form>
          </>}
        </section>
      </main>
      {blocker.state === 'blocked' || confirmAnother ? <PasswordLeaveDialog
        onStay={() => { if (blocker.state === 'blocked') blocker.reset(); setConfirmAnother(false) }}
        onLeave={() => { if (blocker.state === 'blocked') blocker.proceed(); else resetCreatedUser() }}
      /> : null}
    </div>
  )
}
