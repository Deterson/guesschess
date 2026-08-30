import { Client, type StompSubscription } from '@stomp/stompjs'
import { useAuthStore } from '../stores/auth'

/**
 * Même logique que api.ts : en prod, nginx proxifie /ws en same-origin vers le
 * backend (voir frontend/nginx.conf), donc on dérive l'URL du WebSocket de l'origine
 * courante de la page plutôt que de coder un domaine en dur au moment du build.
 */
const WS_URL =
  import.meta.env.VITE_WS_URL ??
  (import.meta.env.DEV
    ? 'ws://localhost:8080/ws'
    : `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`)

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected'

let client: Client | null = null
let status: ConnectionStatus = 'connecting'
const statusListeners = new Set<(status: ConnectionStatus) => void>()
let connectWaiters: Array<{ resolve: (client: Client) => void; reject: (error: Error) => void }> = []

function setStatus(next: ConnectionStatus) {
  status = next
  statusListeners.forEach((listener) => listener(next))
}

/** Appelé immédiatement avec le statut courant, puis à chaque changement. Retourne un désabonnement. */
export function onStatusChange(listener: (status: ConnectionStatus) => void): () => void {
  statusListeners.add(listener)
  listener(status)
  return () => statusListeners.delete(listener)
}

function ensureClient(): Client {
  if (client) return client

  client = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 2000,
  })

  // Réévalué avant CHAQUE tentative de connexion (y compris les reconnexions
  // automatiques), pas seulement à la création du Client - un login pendant la
  // session doit être pris en compte dès la prochaine (re)connexion.
  client.beforeConnect = () => {
    const token = useAuthStore().token
    client!.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
  }

  client.onConnect = () => {
    setStatus('connected')
    const waiters = connectWaiters
    connectWaiters = []
    waiters.forEach((waiter) => waiter.resolve(client!))
  }

  client.onWebSocketClose = () => {
    // stompjs retente seul (reconnectDelay) tant que deactivate() n'est jamais
    // appelé - on ne fait ici que refléter l'état et débloquer les appelants en
    // attente d'une connexion qui ne viendra pas avant la prochaine tentative.
    setStatus('disconnected')
    const waiters = connectWaiters
    connectWaiters = []
    waiters.forEach((waiter) => waiter.reject(new Error('Connexion WebSocket perdue')))
  }

  client.onStompError = (frame) => {
    const err = new Error(frame.headers?.message || 'Erreur STOMP')
    setStatus('disconnected')
    const waiters = connectWaiters
    connectWaiters = []
    waiters.forEach((waiter) => waiter.reject(err))
  }

  client.activate()
  return client
}

/**
 * Résout dès que la connexion STOMP est active. Chaque appel réévalue l'état courant
 * plutôt que de réutiliser une promesse mise en cache pour toute la session : si une
 * tentative précédente a échoué (erreur STOMP, socket coupé), l'appel suivant
 * reconstruit une attente fraîche sur la prochaine connexion réussie au lieu de rester
 * bloqué indéfiniment sur l'échec passé.
 */
export function connect(): Promise<Client> {
  const c = ensureClient()
  if (c.connected) return Promise.resolve(c)
  setStatus('connecting')
  return new Promise((resolve, reject) => {
    connectWaiters.push({ resolve, reject })
  })
}

export async function subscribe<T>(destination: string, onMessage: (payload: T) => void): Promise<StompSubscription> {
  const stompClient = await connect()
  return stompClient.subscribe(destination, (message) => {
    onMessage(message.body ? JSON.parse(message.body) : null)
  })
}

export async function publish<T>(destination: string, payload?: T): Promise<void> {
  const stompClient = await connect()
  stompClient.publish({
    destination,
    body: payload === undefined ? '' : JSON.stringify(payload),
  })
}
