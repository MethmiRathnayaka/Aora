import { useAuth0 } from '@auth0/auth0-react'
import { useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { fetchCurrentUser } from '../../api/transferApi'
import SiteAdminDashboard from './SiteAdminDashboard'
import OperationsDashboard from './OperationsDashboard'

export default function DashboardPage() {
  const { user, logout, getAccessTokenSilently } = useAuth0()
  const navigate = useNavigate()
  const [profile, setProfile] = useState(null)
  const [profileError, setProfileError] = useState('')

  useEffect(() => {
    let cancelled = false
    fetchCurrentUser({ getAccessTokenSilently })
      .then((currentUser) => { if (!cancelled) setProfile(currentUser) })
      .catch(() => { if (!cancelled) setProfileError('Unable to load your account permissions. Refresh to try again.') })
    return () => { cancelled = true }
  }, [getAccessTokenSilently])

  if (!profile && !profileError) return <div className="dashboard-shell"><p className="state-message" role="status">Loading your dashboard...</p></div>
  if (profile?.role === 'SITE_ADMIN') return <SiteAdminDashboard profile={profile} />
  if (profile?.role === 'MANAGER' || profile?.role === 'STORE_MANAGER') return <OperationsDashboard profile={profile} />

  return (
    <div className="dashboard-shell">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">AORA EQUIPMENT</p>
          <h1>Welcome back</h1>
          <p className="muted">Signed in as {user?.name || user?.email}</p>
        </div>
        <button
          onClick={() => logout({ logoutParams: { returnTo: window.location.origin } })}
          className="button button-secondary"
        >
          Sign out
        </button>
      </header>

      <main className="dashboard-content">
        <section className="equipment-panel dashboard-welcome">
          <p className="eyebrow">OPERATIONS</p>
          <h2>Manage your equipment</h2>
          <p className="muted">Browse equipment records, filter the inventory, and check current assignments.</p>
          <button className="button button-primary" onClick={() => navigate('/equipment')}>
            Visit equipment
          </button>
          <button className="button button-secondary" onClick={() => navigate('/transfers')}>
            Manage transfers
          </button>
          {profile?.role === 'MANAGER' ? <button className="button button-secondary" onClick={() => navigate('/users/create')}>Create user</button> : null}
          {profileError ? <p className="error-message" role="alert">{profileError}</p> : null}
        </section>
      </main>
    </div>
  )
}
