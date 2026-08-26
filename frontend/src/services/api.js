const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

/**
 * `credentials: 'include'` fait partir le cookie d'identité anonyme
 * (`guesschess_anon`, posé silencieusement par le backend sur toute requête HTTP) —
 * nécessaire pour que la création/le join en anonyme résolvent la même identité que
 * les futures requêtes du même navigateur.
 */
async function request(path, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    const error = new Error(errorBody?.message || `Erreur ${response.status}`)
    error.status = response.status
    error.code = errorBody?.error
    throw error
  }

  return response.status === 204 ? null : response.json()
}

export const oauthAuthorizationUrl = (provider) => `${API_URL}/oauth2/authorization/${provider}`

export const createGame = (variant, color, token) =>
  request('/api/games', { method: 'POST', body: { variant, color }, token })

export const joinGame = (gameId, playerToken, authToken) =>
  request(`/api/games/${gameId}/join`, { method: 'POST', body: { token: playerToken }, token: authToken })
