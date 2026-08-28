import { defineStore } from 'pinia'
import { ref } from 'vue'
import { connect, publish, subscribe } from '../services/stompClient'
import type { StompSubscription } from '@stomp/stompjs'
import type { ChatMessage, ColorLower, ErrorMessage, GameStateMessage, MySubmissionMessage, PromotionPieceType } from '../types/api'

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
  const pendingMove = ref<{ from: string; to: string } | null>(null)
  /**
   * Dérivé directement de la présence d'un jeton : celui-ci n'arrive jamais dans les
   * mains de ce store autrement que déjà vérifié (par /my-access ou /join, tous deux
   * côté serveur) - contrairement à l'ancien modèle où un jeton pouvait transiter par
   * une URL sans garantie que son porteur soit le bon. NotYourColorException reste un
   * filet de sécurité serveur (jeton forgé) plutôt qu'un cas normal.
   */
  const canAct = ref(false)
  /**
   * Chat ephemere : en memoire uniquement, jamais persiste (ni localStorage ni
   * ailleurs) - vide a chaque (re)join, un rechargement de page perd donc
   * l'historique par design (voir CLAUDE.md).
   */
  const chatMessages = ref<ChatMessage[]>([])

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
    pendingMove.value = null
    canAct.value = Boolean(playerToken)
    chatMessages.value = []

    await connect()

    subscriptions.push(
      await subscribe<ChatMessage>(`/topic/games/${id}/chat`, (payload) => {
        chatMessages.value.push(payload)
      }),
    )
    subscriptions.push(
      await subscribe<GameStateMessage>(`/topic/games/${id}`, (payload) => {
        state.value = payload
        pendingSubmission.value = false
        pendingMove.value = null
      }),
    )
    subscriptions.push(
      await subscribe<GameStateMessage>('/user/queue/game.state', (payload) => {
        state.value = payload
        applyMySubmission(payload.mySubmission)
      }),
    )
    subscriptions.push(
      await subscribe<ErrorMessage>('/user/queue/errors', (payload) => {
        error.value = payload
        pendingSubmission.value = false
        pendingMove.value = null
        if (payload.code === 'NotYourColorException') canAct.value = false
      }),
    )
    subscriptions.push(await subscribe('/user/queue/move.ack', () => {}))
    subscriptions.push(await subscribe('/user/queue/guess.ack', () => {}))

    await publish(`/app/games/${id}/view`, { token: playerToken })
  }

  /**
   * Reflète, après (re)connexion (typiquement un rechargement de page), ma propre
   * soumission déjà enregistrée côté serveur pour le round en cours - jamais celle de
   * l'adversaire, le serveur ne l'envoie de toute façon jamais (voir
   * MySubmissionMessage.java). Sans ça, pendingSubmission repartait toujours à false
   * après un rechargement alors qu'un coup pouvait déjà être enregistré : le joueur
   * pouvait alors retenter un coup que le serveur bloquerait (IllegalStateException,
   * "a move has already been submitted for this round").
   */
  function applyMySubmission(mySubmission: MySubmissionMessage) {
    pendingSubmission.value = mySubmission.submitted
    pendingMove.value = mySubmission.from && mySubmission.to ? { from: mySubmission.from, to: mySubmission.to } : null
  }

  function submitMove(from: string, to: string, promotion: PromotionPieceType | null) {
    pendingSubmission.value = true
    pendingMove.value = { from, to }
    publish(`/app/games/${gameId.value}/move`, { token: token.value, from, to, promotion: promotion ?? null })
  }

  function submitGuess(from: string | null, to: string | null, promotion: PromotionPieceType | null) {
    pendingSubmission.value = true
    pendingMove.value = from && to ? { from, to } : null
    publish(`/app/games/${gameId.value}/guess`, { token: token.value, from, to, promotion: promotion ?? null })
  }

  function dismissError() {
    error.value = null
  }

  function sendChat(text: string) {
    publish(`/app/games/${gameId.value}/chat`, { token: token.value, text })
  }

  return {
    gameId,
    token,
    myColor,
    state,
    error,
    pendingSubmission,
    pendingMove,
    canAct,
    chatMessages,
    joinGame,
    submitMove,
    submitGuess,
    sendChat,
    dismissError,
  }
})
