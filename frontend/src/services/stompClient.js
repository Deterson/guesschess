import { Client } from '@stomp/stompjs'

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws'

let client = null
let connectPromise = null

/**
 * STOMP brut sur le endpoint /ws du backend, sans SockJS (WebSocketConfig.java
 * n'active pas de fallback). Une seule connexion est réutilisée pour toute la session
 * (création de partie sur la home, puis jeu sur GameView).
 */
export function connect() {
  if (connectPromise) return connectPromise

  client = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 2000,
  })

  connectPromise = new Promise((resolve, reject) => {
    client.onConnect = () => resolve(client)
    client.onStompError = (frame) => {
      reject(new Error(frame.headers?.message || 'Erreur STOMP'))
    }
    client.activate()
  })

  return connectPromise
}

export async function subscribe(destination, onMessage) {
  const stompClient = await connect()
  return stompClient.subscribe(destination, (message) => {
    onMessage(message.body ? JSON.parse(message.body) : null)
  })
}

export async function publish(destination, payload) {
  const stompClient = await connect()
  stompClient.publish({
    destination,
    body: payload === undefined ? '' : JSON.stringify(payload),
  })
}
