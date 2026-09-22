import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'
import { ArrowRight, Building2, CircleUserRound, HelpCircle, LogOut, Shovel, Truck } from 'lucide-react'
import DashboardLogo from './DashboardLogo'
import { fetchEquipment } from '../../api/equipmentApi'
import { fetchTransfers } from '../../api/transferApi'
import './SiteAdminDashboard.css'

export default function SiteAdminDashboard({ profile }) {
  const { user, logout, getAccessTokenSilently } = useAuth0()
  const [summary, setSummary] = useState(null)
  const [refresh, setRefresh] = useState(0)
  const site = profile.currentWorksite

  useEffect(() => {
    if (!site?.worksiteId) return
    let cancelled = false
    Promise.allSettled([
      fetchEquipment({ getAccessTokenSilently, page: 0, size: 1, search: '', currentWorksiteId: site.worksiteId }),
      fetchTransfers({ getAccessTokenSilently, status: 'IN_TRANSIT', toWorksiteId: site.worksiteId, size: 1 }),
      fetchTransfers({ getAccessTokenSilently, status: 'IN_TRANSIT', fromWorksiteId: site.worksiteId, size: 1 }),
    ]).then(([equipment, inbound, outbound]) => {
      if (cancelled) return
      setSummary({
        equipment: equipment.status === 'fulfilled' ? equipment.value.totalElements : null,
        transfers: inbound.status === 'fulfilled' && outbound.status === 'fulfilled' ? inbound.value.totalElements + outbound.value.totalElements : null,
      })
    })
    return () => { cancelled = true }
  }, [getAccessTokenSilently, site?.worksiteId, refresh])

  const equipmentCount = !site ? 'No site assigned' : !summary ? 'Loading equipment...' : summary.equipment === null ? 'Count unavailable' : `${summary.equipment} units on-site`
  const transferCount = !site ? 'No site assigned' : !summary ? 'Loading transfers...' : summary.transfers === null ? 'Count unavailable' : `${summary.transfers} pending inbound / outbound`

  return (
    <div className="site-dashboard">
      <header className="site-header">
        <div className="site-header-inner">
          <Link to="/dashboard" className="site-brand" aria-label="AORA home"><DashboardLogo />AORA</Link>
          <button className="site-signout" onClick={() => logout({ logoutParams: { returnTo: window.location.origin } })}><LogOut size={18} aria-hidden="true" />Sign out</button>
        </div>
      </header>
      <main className="site-main">
        <Building2 className="site-watermark" aria-hidden="true" strokeWidth={0.6} />
        <section className="site-welcome" aria-labelledby="site-welcome-heading">
          <div className="site-banner-top"><p className="site-eyebrow">Site operations · {site?.name || 'Site not assigned'}</p><span className="site-status"><i />{site ? (site.status === 'ACTIVE' ? 'Active site' : 'Site inactive') : 'Unassigned'}</span></div>
          <h1 id="site-welcome-heading">Welcome back, {profile.firstName || 'Site Admin'}</h1>
          <p className="account-role">Site admin</p>
          <p className="site-account"><CircleUserRound size={16} aria-hidden="true" />Signed in as {profile.email || user?.email}</p>
          {site?.projectCode ? <p className="site-project">Project {site.projectCode} · Site admin</p> : null}
        </section>
        {!site ? <p className="site-notice" role="status">You haven’t been assigned to a worksite yet. Contact your manager to get started.</p> : null}
        <div className="site-action-grid">
          <section className="site-card site-card-transfers" aria-labelledby="site-transfers-title">
            <div className="site-card-top"><span className="site-action-icon"><Truck size={32} aria-hidden="true" /></span><span className="site-count" aria-live="polite"><i />{transferCount}</span></div>
            <h2 id="site-transfers-title">Manage Transfers</h2>
            <p>Coordinate relocations and site handoffs.</p>
            <Link className="site-action-link" to="/transfers/manage?mode=receive">Open Transfer Manager<ArrowRight size={20} aria-hidden="true" /></Link>
          </section>
          <section className="site-card site-card-equipment" aria-labelledby="site-equipment-title">
            <div className="site-card-top"><span className="site-action-icon"><Shovel size={32} aria-hidden="true" /></span><span className="site-count" aria-live="polite"><i />{equipmentCount}</span></div>
            <h2 id="site-equipment-title">Visit Equipment</h2>
            <p>Inspect active units and verify status.</p>
            <Link className="site-action-link" to="/equipment">Open Equipment Catalog<ArrowRight size={20} aria-hidden="true" /></Link>
          </section>
        </div>
        {summary && (summary.equipment === null || summary.transfers === null) ? <div className="site-notice" role="status">Some counts could not be loaded. <button onClick={() => { setSummary(null); setRefresh((value) => value + 1) }}>Retry</button></div> : null}
        <footer className="site-footer"><details><summary><HelpCircle size={16} aria-hidden="true" />AORA Site Access Portal · Need dispatch support? <span>Tap Help</span></summary><p>Contact your manager or store manager for dispatch support. Include your site name{site ? ` (${site.name})` : ''} and equipment code when reporting a transfer issue.</p></details></footer>
      </main>
    </div>
  )
}
