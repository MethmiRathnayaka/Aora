import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link } from 'react-router-dom'
import { ArrowRight, ArrowLeftRight, Boxes, LogOut, ShieldCheck, Truck, Receipt } from 'lucide-react'
import { fetchEquipment } from '../../api/equipmentApi'
import { fetchTransfers } from '../../api/transferApi'
import DashboardLogo from './DashboardLogo'
import './OperationsDashboard.css'

export default function OperationsDashboard({ profile }) {
  const { logout, getAccessTokenSilently } = useAuth0()
  const [stats, setStats] = useState(null)
  const [attempt, setAttempt] = useState(0)
  const isManager = profile.role === 'MANAGER'
  const roleName = isManager ? 'Manager' : 'Store manager'

  useEffect(() => {
    let cancelled = false
    const equipment = (status) => fetchEquipment({ getAccessTokenSilently, page: 0, size: 1, search: '', status })
    Promise.allSettled([
      equipment(), equipment('ON_SITE'), equipment('UNDER_MAINTENANCE'),
      fetchTransfers({ getAccessTokenSilently, status: 'IN_TRANSIT', size: 1 }),
    ]).then((results) => {
      if (!cancelled) setStats(results.map((result) => result.status === 'fulfilled' ? result.value.totalElements : null))
    })
    return () => { cancelled = true }
  }, [getAccessTokenSilently, attempt])

  const portals = [
    { title: 'Equipments', description: 'Browse equipment, check current assignments, and inspect unit condition across worksites.', icon: <Boxes />, to: '/equipment', action: 'Open' },
    { title: 'Rental Bills', description: 'Generate a worksite rental bill for a selected return period, review charges, and print or save it as PDF.', icon: <Receipt />, to: '/rental-bills', action: 'Create Bill' },
    isManager
      ? { title: 'Team & Site Access', description: 'Browse team accounts, check contact details and roles, and review assigned worksites.', icon: <ShieldCheck />, to: '/users', action: 'View Users' }
      : { title: 'Receive Equipment', description: 'Review incoming transfers, verify equipment condition, and complete handoffs to your store.', icon: <Truck />, to: '/transfers/manage?mode=receive', action: 'Receive Transfers' },
    { title: 'Operations & Transfers', description: `Coordinate worksite relocations, log dispatches, and track machinery handoffs.${stats?.[3] != null ? ` ${stats[3]} transfers currently in transit.` : ''}`, icon: <ArrowLeftRight />, to: '/transfers', action: 'View Transfers' },
  ]

  return (
    <div className="operations-dashboard">
      <header className="operations-header"><div className="operations-header-inner">
        <Link to="/dashboard" className="operations-brand"><DashboardLogo /><span><strong>AORA</strong><small>Equipment & Operations</small></span></Link>
        <div className="operations-account-actions"><div className="operations-account"><i /><span><strong>{profile.firstName || roleName}</strong><small>{profile.email}</small></span></div><button className="operations-button" onClick={() => logout({ logoutParams: { returnTo: window.location.origin } })}><LogOut size={18} />Sign out</button></div>
      </div></header>
      <main className="operations-main">
        <section className="operations-greeting"><p className="operations-eyebrow">AORA Equipment</p><h1>Welcome back</h1><p className="operations-identity">Signed in as <strong>{profile.email}</strong><span aria-hidden="true">·</span><span className="operations-role"><ShieldCheck size={16} />{roleName}</span></p></section>
        <section className="operations-hero"><span className="operations-tag">Operations</span><h2>Manage your equipment</h2><p>Browse equipment records, filter the active inventory, dispatch unit transfers, and monitor site assignments.</p></section>
        {stats?.some((value) => value === null) ? <p className="operations-error" role="status">Some dashboard counts are unavailable. <button onClick={() => { setStats(null); setAttempt((value) => value + 1) }}>Retry</button></p> : null}
        <section><div className="operations-section-heading"><h2>Core Operational Portals</h2><span>Primary Hubs</span></div><div className="operations-portals">{portals.map((portal) => <Link className="operations-portal" key={portal.title} to={portal.to}><span className="operations-icon">{portal.icon}</span><h3>{portal.title}</h3><p>{portal.description}</p><span className="operations-portal-action">{portal.action}<ArrowRight size={18} /></span></Link>)}</div></section>
      </main>
      <footer className="operations-footer"><div><p><strong>AORA CONSTRUCTION</strong><span>·</span>Civil Operations & Heavy Fleet Division</p><details><summary>Support & Dispatch</summary><p>Contact your operations manager for dispatch support. Include the equipment code and transfer details.</p></details></div></footer>
    </div>
  )
}
