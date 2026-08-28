import { Client, type StompSubscription } from '@stomp/stompjs'

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

let client: Client | null = null
let connectPromise: Promise<Client> | null = null

/**
 * STOMP brut sur le endpoint /ws du backend, sans SockJS (WebSocketConfig.java
 * n'active pas de fallback). Une seule connexion est réutilisée pour toute la session
 * (création de partie sur la home, puis jeu sur GameView).
 */
export function connect(): Promise<Client> {
  if (connectPromise) return connectPromise

  client = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 2000,
  })

  connectPromise = new Promise((resolve, reject) => {
    client!.onConnect = () => resolve(client!)
    client!.onStompError = (frame) => {
      reject(new Error(frame.headers?.message || 'Erreur STOMP'))
    }
    client!.activate()
  })

  return connectPromise
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
