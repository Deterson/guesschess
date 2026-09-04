import { useAuthStore } from '../stores/auth'
import { i18n } from '../i18n'
import type {
  AccountResponse,
  AccountSettingsHttpResponse,
  Color,
  CompleteRegistrationHttpResponse,
  CreateGameHttpResponse,
  ErrorResponse,
  GameHistoryHttpResponse,
  GamePlayersHttpResponse,
  GameSummaryHttpResponse,
  GameVariant,
  JoinGameHttpResponse,
  MyAccessHttpResponse,
  PublicProfileHttpResponse,
  TimeControlHttpRequest,
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
  /**
   * Réservé aux routes qui restent utilisables sans compte (permitAll côté backend :
   * createGame, joinGame, myAccess). Pour celles-là seulement, un jeton périmé/corrompu
   * en localStorage ne doit jamais bloquer un flux qui reste possible en anonyme : on
   * l'oublie et on retente une seule fois sans lui. Pour toute autre route (qui exige
   * toujours un compte), voir le code SESSION_EXPIRED plus bas.
   */
  allowAnonymousFallback?: boolean
}

/**
 * `credentials: 'include'` fait partir le cookie d'identité anonyme
 * (`guesschess_anon`, posé silencieusement par le backend sur toute requête HTTP) —
 * nécessaire pour que la création/le join en anonyme résolvent la même identité que
 * les futures requêtes du même navigateur.
 */
async function request<T>(
  path: string,
  { method = 'GET', body, token, allowAnonymousFallback = false }: RequestOptions = {},
): Promise<T> {
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
    // équivalent à une requête anonyme.
    useAuthStore().logout()
    if (allowAnonymousFallback) {
      return request<T>(path, { method, body })
    }
    // Route qui exige toujours un compte (getMe, listMyGames, setLogin, ...) : pas de
    // repli anonyme possible, donc pas la peine de retenter. On distingue ce cas via
    // le code SESSION_EXPIRED pour que l'appelant puisse réagir (ex. renvoyer vers
    // l'accueil) plutôt que d'afficher un 401 brut à un utilisateur qui se croit
    // toujours connecté.
    throw new ApiError(i18n.global.t('errors.sessionExpired'), 401, 'SESSION_EXPIRED')
  }

  if (!response.ok) {
    const errorBody: ErrorResponse | null = await response.json().catch(() => null)
    throw new ApiError(errorBody?.message || i18n.global.t('errors.generic', { status: response.status }), response.status, errorBody?.error)
  }

  return response.status === 204 ? (null as T) : response.json()
}

export const oauthAuthorizationUrl = (provider: string): string => `${API_URL}/oauth2/authorization/${provider}`

export const createGame = (
  variant: GameVariant,
  color: Color | 'RANDOM',
  timeControl: TimeControlHttpRequest | null,
  token: string | null,
) => request<CreateGameHttpResponse>('/api/games', { method: 'POST', body: { variant, color, timeControl }, token, allowAnonymousFallback: true })

export const joinGame = (gameId: string, authToken: string | null) =>
  request<JoinGameHttpResponse>(`/api/games/${gameId}/join`, { method: 'POST', token: authToken, allowAnonymousFallback: true })

export const myAccess = (gameId: string, authToken: string | null) =>
  request<MyAccessHttpResponse>(`/api/games/${gameId}/my-access`, { token: authToken, allowAnonymousFallback: true })

export const getGameHistory = (gameId: string) =>
  request<GameHistoryHttpResponse>(`/api/games/${gameId}/history`)

export const getGamePlayers = (gameId: string) =>
  request<GamePlayersHttpResponse>(`/api/games/${gameId}/players`)

export const getMe = (authToken: string) =>
  request<AccountResponse>('/api/account/me', { token: authToken })

export const updateDisplayName = (displayName: string, authToken: string) =>
  request<AccountResponse>('/api/account/me', { method: 'PATCH', body: { displayName }, token: authToken })

export const updateBio = (bio: string, authToken: string) =>
  request<AccountResponse>('/api/account/bio', { method: 'PATCH', body: { bio }, token: authToken })

/** Pose le login d'un compte historique (etape 14) - deja authentifie, voir stores/account.ts. */
export const setLogin = (login: string, authToken: string) =>
  request<AccountResponse>('/api/account/login', { method: 'PATCH', body: { login }, token: authToken })

/**
 * Cree le compte pour un pendingToken d'inscription frais (etape 14) - jamais de
 * jeton d'authentification ici, l'identite est portee par pendingToken lui-meme
 * (voir RegistrationController).
 */
export const completeRegistration = (pendingToken: string, login: string) =>
  request<CompleteRegistrationHttpResponse>('/api/registration/complete', { method: 'POST', body: { pendingToken, login } })

export const listMyGames = (page: number, size: number, authToken: string) =>
  request<GameSummaryHttpResponse[]>(`/api/account/games?page=${page}&size=${size}`, { token: authToken })

/** Profil public d'un joueur (etape 15) - jamais de jeton, endpoint accessible a tous. */
export const getPublicProfile = (login: string) =>
  request<PublicProfileHttpResponse>(`/api/players/${encodeURIComponent(login)}`)

export const listGamesByLogin = (login: string, page: number, size: number) =>
  request<GameSummaryHttpResponse[]>(`/api/players/${encodeURIComponent(login)}/games?page=${page}&size=${size}`)

export const getAccountSettings = (authToken: string) =>
  request<AccountSettingsHttpResponse>('/api/account/settings', { token: authToken })

export const updateAccountSettings = (settings: AccountSettingsHttpResponse, authToken: string) =>
  request<AccountSettingsHttpResponse>('/api/account/settings', { method: 'PATCH', body: settings, token: authToken })
