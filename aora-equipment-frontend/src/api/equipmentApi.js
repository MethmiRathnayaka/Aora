const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

export async function fetchEquipment({ getAccessTokenSilently, page, size, search, status, condition, currentWorksiteId }) {
  const token = await getAccessTokenSilently()
  const params = new URLSearchParams({ page: String(page), size: String(size) })

  if (search.trim()) params.set('search', search.trim())
  if (status) params.set('status', status)
  if (condition) params.set('condition', condition)
  if (currentWorksiteId) params.set('currentWorksiteId', String(currentWorksiteId))

  const response = await fetch(`${apiBaseUrl}/equipment?${params}`, {
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    throw new Error(`Equipment request failed (${response.status})`)
  }

  return response.json()
}

async function equipmentRequest({ getAccessTokenSilently, path, method = 'GET', body }) {
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
    let message = `Equipment request failed (${response.status})`
    try {
      const details = await response.json()
      message = details.message || details.error || message
    } catch {
      // Keep the status-based message when the server has no JSON error body.
    }
    throw new Error(message)
  }

  return response.json()
}

export async function fetchEquipmentTypes({ getAccessTokenSilently }) {
  const content = []
  let page = 0
  let result
  do {
    result = await equipmentRequest({ getAccessTokenSilently, path: `/equipment-types?page=${page}&size=100&sort=name,asc` })
    content.push(...(result.content ?? []))
    page += 1
  } while (page < result.totalPages)
  return { ...result, content }
}

export function createEquipmentType({ getAccessTokenSilently, name }) {
  return equipmentRequest({ getAccessTokenSilently, path: '/equipment-types', method: 'POST', body: { name } })
}

export function createEquipment({ getAccessTokenSilently, equipment }) {
  return equipmentRequest({ getAccessTokenSilently, path: '/equipment', method: 'POST', body: equipment })
}

export function retireEquipment({ getAccessTokenSilently, equipmentCode }) {
  return equipmentRequest({ getAccessTokenSilently, path: `/equipment/${encodeURIComponent(equipmentCode)}`, method: 'PATCH', body: { status: 'RETIRED' } })
}

export function startMaintenance({ getAccessTokenSilently, equipmentCode, reason, description }) {
  return equipmentRequest({ getAccessTokenSilently, path: '/maintenance/start', method: 'POST', body: { equipmentCode, reason, description } })
}

export function completeMaintenance({ getAccessTokenSilently, equipmentCode }) {
  return equipmentRequest({ getAccessTokenSilently, path: `/maintenance/equipment/${encodeURIComponent(equipmentCode)}/complete`, method: 'POST' })
}

