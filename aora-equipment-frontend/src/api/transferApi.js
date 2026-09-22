const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

async function request({ getAccessTokenSilently, path, method = 'GET', body }) {
  const token = await getAccessTokenSilently()
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const details = await response.json()
      message = details.message || details.error || message
    } catch {
      // Keep the status-based message when the server has no JSON error body.
    }
    throw new Error(message)
  }

  return response.status === 204 ? null : response.json()
}

export function fetchTransfers({ getAccessTokenSilently, status, equipmentCode, toWorksiteId, fromWorksiteId, page = 0, size = 100 }) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (fromWorksiteId) params.set('fromWorksiteId', String(fromWorksiteId))
  if (status) params.set('status', status)
  if (equipmentCode) params.set('equipmentCode', equipmentCode)
  if (toWorksiteId) params.set('toWorksiteId', String(toWorksiteId))
  return request({ getAccessTokenSilently, path: `/transfers?${params}` })
}

export function fetchCurrentUser({ getAccessTokenSilently }) {
  return request({ getAccessTokenSilently, path: '/users/me' })
}

export function fetchDirectory({ getAccessTokenSilently, resource, id, page = 0 }) {
  if (!['users', 'worksites'].includes(resource)) throw new Error('Unknown directory')
  const path = id
    ? `/${resource}/${encodeURIComponent(id)}`
    : `/${resource}?page=${page}&size=20&sort=${resource === 'users' ? 'id' : 'name'},asc`
  return request({ getAccessTokenSilently, path })
}

export function fetchWorksites({ getAccessTokenSilently, page = 0 }) {
  return request({ getAccessTokenSilently, path: `/worksites?page=${page}&size=100` })
}

export function createTransfer({ getAccessTokenSilently, transfer }) {
  return request({ getAccessTokenSilently, path: '/transfers', method: 'POST', body: transfer })
}

export function completeTransfer({ getAccessTokenSilently, transferId, equipmentCode, conditionAfter, unitPrice }) {
  return request({
    getAccessTokenSilently,
    path: `/transfers/${transferId}`,
    method: 'PATCH',
    body: {
      status: 'COMPLETED',
      unitPrice,
      equipment: { equipmentCode, conditionAfter },
    },
  })
}

export function fetchStoreReturnEquipment({ getAccessTokenSilently, equipmentCode }) {
  return request({ getAccessTokenSilently, path: `/equipment/${encodeURIComponent(equipmentCode)}` })
}

export function receiveAtStore({ getAccessTokenSilently, receipt }) {
  return request({ getAccessTokenSilently, path: '/transfers/receive-at-store', method: 'POST', body: receipt })
}

export function createWorksite({ getAccessTokenSilently, worksite }) {
  return request({ getAccessTokenSilently, path: '/worksites', method: 'POST', body: worksite })
}

export function deleteTransfer({ getAccessTokenSilently, transferId }) {
  return request({ getAccessTokenSilently, path: `/transfers/${transferId}`, method: 'DELETE' })
}

export function fetchRentalBill({ getAccessTokenSilently, worksiteId, fromDate, toDate }) {
  const params = new URLSearchParams({ worksiteId, fromDate, toDate })
  return request({ getAccessTokenSilently, path: `/rental-bills?${params}` })
}
