import { useEffect, useRef, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { fetchCurrentUser, fetchDirectory } from '../../api/transferApi'
import { deleteUser } from '../../api/userApi'
import RecordName from '../../components/RecordName'

const label = (value) => value ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (letter) => letter.toUpperCase()) : 'Not available'

function details(record, users) {
  return users ? [
    ['Name', [record.firstName, record.lastName].filter(Boolean).join(' ')],
    ['Email', record.email],
    ['Phone', record.phone],
    ['Role', label(record.role)],
    ['Status', record.isActive ? 'Active' : 'Inactive'],
    ['Assigned worksite', record.currentWorksite ? <Link to={`/worksites/${record.currentWorksite.worksiteId}`}>{record.currentWorksite.name}</Link> : 'Not assigned'],
  ] : [
    ['Name', record.name],
    ['Project code', record.projectCode],
    ['Status', label(record.status)],
    ['Address', record.address],
    ['Site admin', record.siteAdminId ? <Link to={`/users/${record.siteAdminId}`}><RecordName resource="users" id={record.siteAdminId} /></Link> : 'Not assigned'],
  ]
}

export default function DirectoryPage({ resource }) {
  const { getAccessTokenSilently } = useAuth0()
  const { id } = useParams()
  const navigate = useNavigate()
  const [profile, setProfile] = useState(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const deletingRef = useRef(false)

  async function removeUser(record) {
    if (deletingRef.current || !window.confirm(`Delete ${record.email}? This removes their application access and assignments. Their past activity will remain in history.`)) return
    deletingRef.current = true
    setDeleting(true)
    setDeleteError('')
    try {
      await deleteUser({ getAccessTokenSilently, userId: record.userId })
      if (id) navigate('/users')
      else setAttempt((value) => value + 1)
    } catch (error) {
      setDeleteError(error.message || 'Unable to delete user.')
    } finally {
      deletingRef.current = false
      setDeleting(false)
    }
  }

  const users = resource === 'users'
  const title = users ? 'Users' : 'Worksites'
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ loading: true })

  useEffect(() => {
    let cancelled = false
    async function load() {
      setState({ loading: true })
      try {
        const profile = await fetchCurrentUser({ getAccessTokenSilently })
        if (cancelled) return
        if (profile.role !== 'MANAGER') {
          setState({ denied: true })
          return
        }
        setProfile(profile)
        const data = await fetchDirectory({ getAccessTokenSilently, resource, id, page })
        if (!cancelled) {
          setState({ data })
          if (!id && page > 0 && page >= data.totalPages) setPage(Math.max(0, data.totalPages - 1))
        }
      } catch (error) {
        if (!cancelled) setState({ error: error.message || 'Unable to load details.' })
      }
    }
    load()
    return () => { cancelled = true }
  }, [getAccessTokenSilently, resource, id, page, attempt])

  const rows = state.data?.content ?? []
  return (
    <div className="dashboard-shell">
      <header className="dashboard-header">
        <div><p className="eyebrow">AORA EQUIPMENT</p><h1>{id ? `${users ? 'User' : 'Worksite'} details` : title}</h1><p className="muted">{users ? 'Review team contact details, roles, and worksite assignments.' : 'Review project information, status, and assigned site admins.'}</p></div>
        <div className="header-actions"><Link className="button button-secondary" to="/dashboard">Dashboard</Link><Link className="button button-secondary" to={users ? '/worksites' : '/users'}>{users ? 'Worksites' : 'Users'}</Link></div>
      </header>
      <main className="dashboard-content"><section className="equipment-panel">
        {deleteError ? <p className="error-message" role="alert">{deleteError}</p> : null}
        {state.loading ? <p className="state-message" role="status">Loading {title.toLowerCase()}...</p> : state.denied ? <p className="state-message" role="alert">Only managers can access this directory.</p> : state.error ? <div className="state-message"><p className="error-message" role="alert">{state.error}</p><button className="button button-secondary" onClick={() => setAttempt((value) => value + 1)}>Try again</button></div> : state.data ? <>
          <div className="panel-heading"><h2>{id ? 'Details' : `${state.data.totalElements} ${title.toLowerCase()}`}</h2><Link className="button button-secondary" to={id ? `/${resource}` : `/${resource}/create`}>{id ? `Back to ${title.toLowerCase()}` : `Create ${users ? 'user' : 'worksite'}`}</Link></div>
          {users && id && state.data.userId !== profile?.userId ? <button className="button button-secondary" disabled={deleting} onClick={() => removeUser(state.data)}>{deleting ? 'Deleting...' : 'Delete user'}</button> : null}
          {id ? <div className="table-wrap"><table><tbody>{details(state.data, users).map(([name, value]) => <tr key={name}><th scope="row">{name}</th><td style={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{value || 'Not provided'}</td></tr>)}</tbody></table></div> : <>
            {rows.length === 0 ? <p className="state-message">No {title.toLowerCase()} found.</p> : <div className="table-wrap"><table>
              <thead><tr>{(users ? ['Name', 'Email', 'Role', 'Status', 'Worksite', 'Details'] : ['Name', 'Project code', 'Status', 'Site admin', 'Details']).map((name) => <th scope="col" key={name}>{name}</th>)}</tr></thead>
              <tbody>{rows.map((record) => <tr key={users ? record.userId : record.worksiteId}>
                <td>{users ? [record.firstName, record.lastName].filter(Boolean).join(' ') || 'Not provided' : record.name}</td>
                <td>{users ? record.email : record.projectCode}</td>
                {users ? <td>{label(record.role)}</td> : null}
                <td>{users ? (record.isActive ? 'Active' : 'Inactive') : label(record.status)}</td>
                <td>{users ? (record.currentWorksite ? <Link to={`/worksites/${record.currentWorksite.worksiteId}`}>{record.currentWorksite.name}</Link> : 'Not assigned') : (record.siteAdminId ? <Link to={`/users/${record.siteAdminId}`}><RecordName resource="users" id={record.siteAdminId} /></Link> : 'Not assigned')}</td>
                <td><Link to={`/${resource}/${users ? record.userId : record.worksiteId}`} aria-label={`View details for ${users ? record.email : record.name}`}>View details</Link>{users && record.userId !== profile?.userId ? <button className="button button-secondary" disabled={deleting} onClick={() => removeUser(record)} aria-label={`Delete ${record.email}`}>Delete user</button> : null}</td>
              </tr>)}</tbody>
            </table></div>}
            <div className="pagination"><span>Page {page + 1} of {Math.max(1, state.data.totalPages)}</span><div><button className="button button-secondary" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button><button className="button button-secondary" disabled={page + 1 >= state.data.totalPages} onClick={() => setPage((value) => value + 1)}>Next</button></div></div>
          </>}
        </> : null}
      </section></main>
    </div>
  )
}
