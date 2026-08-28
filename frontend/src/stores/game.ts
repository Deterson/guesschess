import { defineStore } from 'pinia'
import { ref } from 'vue'
import { connect, publish, subscribe } from '../services/stompClient'
import type { StompSubscription } from '@stomp/stompjs'
import type { ColorLower, ErrorMessage, GameStateMessage, PromotionPieceType } from '../types/api'

interface JoinGameParams {
  gameId: string
  token?: string | null
  color?: ColorLower | null
}

/**
 * pendingSubmission couvre le round en cours : mis à true dès que je soumets mon
 * coup/devinette, remis à false dès qu'un nouvel état arrive (ack privé si je suis
 * arrivé en premier, ou diffusion publique si ma soumission a résolu le round). Le
 * serveur ne dit jamais explicitement "round résolu" autrement que par ce nouvel état.
 */
export const useGameStore = defineStore('game', () => {
  const gameId = ref<string | null>(null)
  const token = ref<string | null>(null)
  const myColor = ref<ColorLower | null>(null)
  const state = ref<GameStateMessage | null>(null)
  const error = ref<ErrorMessage | null>(null)
  const pendingSubmission = ref(false)
  /**
   * Dérivé directement de la présence d'un jeton : celui-ci n'arrive jamais dans les
   * mains de ce store autrement que déjà vérifié (par /my-access ou /join, tous deux
   * côté serveur) - contrairement à l'ancien modèle où un jeton pouvait transiter par
   * une URL sans garantie que son porteur soit le bon. NotYourColorException reste un
   * filet de sécurité serveur (jeton forgé) plutôt qu'un cas normal.
   */
  const canAct = ref(false)

  let subscriptions: StompSubscription[] = []

  async function joinGame({ gameId: id, token: playerToken = null, color = null }: JoinGameParams) {
    subscriptions.forEach((sub) => sub.unsubscribe())
    subscriptions = []

    gameId.value = id
    token.value = playerToken
    myColor.value = color
    state.value = null
    error.value = null
    pendingSubmission.value = false
    canAct.value = Boolean(playerToken)

    await connect()

    subscriptions.push(
      await subscribe<GameStateMessage>(`/topic/games/${id}`, (payload) => {
        state.value = payload
        pendingSubmission.value = false
      }),
    )
    subscriptions.push(
      await subscribe<GameStateMessage>('/user/queue/game.state', (payload) => {
        state.value = payload
      }),
    )
    subscriptions.push(
      await subscribe<ErrorMessage>('/user/queue/errors', (payload) => {
        error.value = payload
        pendingSubmission.value = false
        if (payload.code === 'NotYourColorException') canAct.value = false
      }),
    )
    subscriptions.push(await subscribe('/user/queue/move.ack', () => {}))
    subscriptions.push(await subscribe('/user/queue/guess.ack', () => {}))

    await publish(`/app/games/${id}/view`)
  }

  function submitMove(from: string, to: string, promotion: PromotionPieceType | null) {
    pendingSubmission.value = true
    publish(`/app/games/${gameId.value}/move`, { token: token.value, from, to, promotion: promotion ?? null })
  }

  function submitGuess(from: string | null, to: string | null, promotion: PromotionPieceType | null) {
    pendingSubmission.value = true
    publish(`/app/games/${gameId.value}/guess`, { token: token.value, from, to, promotion: promotion ?? null })
  }

  function dismissError() {
    error.value = null
  }

  return {
    gameId,
    token,
    myColor,
    state,
    error,
    pendingSubmission,
    canAct,
    joinGame,
    submitMove,
    submitGuess,
    dismissError,
  }
})
