const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

export async function createUser({ getAccessTokenSilently, user }) {
  const token = await getAccessTokenSilently()
  const response = await fetch(`${apiBaseUrl}/users`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(user),
  })

  if (!response.ok) {
    let message = `User creation failed (${response.status})`
    try {
      const details = await response.json()
      message = details.message || details.error || message
    } catch {
      // Keep the status-based message for responses without a JSON body.
    }
    if (response.status === 409 || /the user already exists/i.test(message)) {
      message = 'An account with this email address already exists. Check the Users page or use a different email address.'
    }
    throw new Error(message)
  }

  return response.json()
}

export async function deleteUser({ getAccessTokenSilently, userId }) {
  const token = await getAccessTokenSilently()
  const response = await fetch(`${apiBaseUrl}/users/${userId}`, {
    method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    const details = await response.json().catch(() => ({}))
    throw new Error(details.message || details.error || `User deletion failed (${response.status})`)
  }
}
