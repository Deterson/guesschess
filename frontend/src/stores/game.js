import { defineStore } from 'pinia'
import { ref } from 'vue'
import { connect, publish, subscribe } from '../services/stompClient'

/**
 * pendingSubmission couvre le round en cours : mis à true dès que je soumets mon
 * coup/devinette, remis à false dès qu'un nouvel état arrive (ack privé si je suis
 * arrivé en premier, ou diffusion publique si ma soumission a résolu le round). Le
 * serveur ne dit jamais explicitement "round résolu" autrement que par ce nouvel état.
 */
export const useGameStore = defineStore('game', () => {
  const gameId = ref(null)
  const token = ref(null)
  const myColor = ref(null)
  const state = ref(null)
  const error = ref(null)
  const pendingSubmission = ref(false)

  let subscriptions = []

  async function createGame(variant = 'GUESSCHESS') {
    await connect()
    return new Promise((resolve) => {
      subscribe('/user/queue/games.created', (payload) => resolve(payload))
      publish('/app/games.create', { variant })
    })
  }

  async function joinGame({ gameId: id, token: playerToken, color }) {
    subscriptions.forEach((sub) => sub.unsubscribe())
    subscriptions = []

    gameId.value = id
    token.value = playerToken
    myColor.value = color
    state.value = null
    error.value = null
    pendingSubmission.value = false

    await connect()

    subscriptions.push(
      await subscribe(`/topic/games/${id}`, (payload) => {
        state.value = payload
        pendingSubmission.value = false
      }),
    )
    subscriptions.push(
      await subscribe('/user/queue/game.state', (payload) => {
        state.value = payload
      }),
    )
    subscriptions.push(
      await subscribe('/user/queue/errors', (payload) => {
        error.value = payload
        pendingSubmission.value = false
      }),
    )
    subscriptions.push(await subscribe('/user/queue/move.ack', () => {}))
    subscriptions.push(await subscribe('/user/queue/guess.ack', () => {}))

    await publish(`/app/games/${id}/view`)
  }

  function submitMove(from, to, promotion) {
    pendingSubmission.value = true
    publish(`/app/games/${gameId.value}/move`, { token: token.value, from, to, promotion: promotion ?? null })
  }

  function submitGuess(from, to, promotion) {
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
    createGame,
    joinGame,
    submitMove,
    submitGuess,
    dismissError,
  }
})
