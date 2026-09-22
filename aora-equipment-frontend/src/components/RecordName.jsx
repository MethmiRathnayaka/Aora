import { useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { fetchDirectory } from '../api/transferApi'

export default function RecordName({ resource = 'worksites', id, name }) {
  const { getAccessTokenSilently } = useAuth0()
  const [record, setRecord] = useState(null)
  useEffect(() => {
    if (!id || name) return
    let cancelled = false
    fetchDirectory({ getAccessTokenSilently, resource, id })
      .then((data) => {
        if (!cancelled) setRecord({ id, resource, name: resource === 'users' ? [data.firstName, data.lastName].filter(Boolean).join(' ') || data.email : data.name })
      })
      .catch(() => { if (!cancelled) setRecord({ id, resource, name: 'Name unavailable' }) })
    return () => { cancelled = true }
  }, [getAccessTokenSilently, resource, id, name])
  if (name) return name
  if (!id) return 'Not assigned'
  return record?.id === id && record.resource === resource ? record.name || 'Name unavailable' : 'Loading name…'
}
