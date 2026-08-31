import { useAuthStore } from '../stores/auth'
import { i18n } from '../i18n'
import type {
  AccountResponse,
  Color,
  CreateGameHttpResponse,
  ErrorResponse,
  GameHistoryHttpResponse,
  GameSummaryHttpResponse,
  GameVariant,
  JoinGameHttpResponse,
  MyAccessHttpResponse,
} from '../types/api'

/**
 * En dev (`npm run dev`), le frontend (5173) et le backend (8080) sont deux origines
 * distinctes. En prod, nginx sert le frontend et proxifie /api, /ws, /oauth2, /login
 * vers le backend en same-origin (voir frontend/nginx.conf) - '' (chaine vide) donne
 * des URLs relatives, donc la même image fonctionne derrière nginx quel que soit le
 * domaine, sans avoir besoin de le connaître au moment du build.
 */
const API_URL = import.meta.env.VITE_API_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '')

const REQUEST_TIMEOUT_MS = 15000

export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  token?: string | null
}

/**
 * `credentials: 'include'` fait partir le cookie d'identité anonyme
 * (`guesschess_anon`, posé silencieusement par le backend sur toute requête HTTP) —
 * nécessaire pour que la création/le join en anonyme résolvent la même identité que
 * les futures requêtes du même navigateur.
 */
async function request<T>(path: string, { method = 'GET', body, token }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  let response: Response
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
  } catch (e) {
    const timedOut = e instanceof DOMException && e.name === 'AbortError'
    throw new ApiError(
      timedOut ? i18n.global.t('errors.timeout') : i18n.global.t('errors.network'),
      0,
      timedOut ? 'TIMEOUT' : 'NETWORK_ERROR',
    )
  } finally {
    clearTimeout(timeoutId)
  }

  if (response.status === 401 && token) {
    // Spring Security rejette une requête portant un Bearer invalide/expiré avec 401
    // AVANT même d'atteindre l'endpoint, même quand celui-ci n'exige aucune
    // authentification (permitAll) - un jeton présenté mais invalide n'est pas
    // équivalent à une requête anonyme. Un compte périmé/corrompu en localStorage ne
    // doit jamais bloquer un flux qui reste possible en anonyme : on l'oublie et on
    // retente une seule fois sans lui.
    useAuthStore().logout()
    return request<T>(path, { method, body })
  }

  if (!response.ok) {
    const errorBody: ErrorResponse | null = await response.json().catch(() => null)
    throw new ApiError(errorBody?.message || i18n.global.t('errors.generic', { status: response.status }), response.status, errorBody?.error)
  }

  return response.status === 204 ? (null as T) : response.json()
}

export const oauthAuthorizationUrl = (provider: string): string => `${API_URL}/oauth2/authorization/${provider}`

export const createGame = (variant: GameVariant, color: Color | 'RANDOM', token: string | null) =>
  request<CreateGameHttpResponse>('/api/games', { method: 'POST', body: { variant, color }, token })

export const joinGame = (gameId: string, authToken: string | null) =>
  request<JoinGameHttpResponse>(`/api/games/${gameId}/join`, { method: 'POST', token: authToken })

export const myAccess = (gameId: string, authToken: string | null) =>
  request<MyAccessHttpResponse>(`/api/games/${gameId}/my-access`, { token: authToken })

export const getGameHistory = (gameId: string) =>
  request<GameHistoryHttpResponse>(`/api/games/${gameId}/history`)

export const getMe = (authToken: string) =>
  request<AccountResponse>('/api/account/me', { token: authToken })

export const updateDisplayName = (displayName: string, authToken: string) =>
  request<AccountResponse>('/api/account/me', { method: 'PATCH', body: { displayName }, token: authToken })

export const listMyGames = (page: number, size: number, authToken: string) =>
  request<GameSummaryHttpResponse[]>(`/api/account/games?page=${page}&size=${size}`, { token: authToken })
