import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { connect, onStatusChange, publish, subscribe, type ConnectionStatus } from '../services/stompClient'
import { getGameHistory } from '../services/api'
import type { StompSubscription } from '@stomp/stompjs'
import type {
  Board,
  ChatMessage,
  ColorLower,
  ErrorMessage,
  GameHistoryEntry,
  GameStateMessage,
  MySubmissionMessage,
  PromotionPieceType,
} from '../types/api'

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
  const connectionStatus = ref<ConnectionStatus>('connecting')
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

  /**
   * Historique detaille rond par rond (etape 11), charge une fois via REST puis
   * rafraichi a chaque nouveau round resolu (watch sur roundCount ci-dessous) -
   * jamais via une souscription STOMP dediee, pour ne pas dupliquer cote frontend la
   * logique deja portee par GET /api/games/{id}/history. historyIndex pilote la
   * navigation cote client, independamment de state : null = position live, -1 =
   * position de depart, 0..n-1 = position apres le round i.
   */
  const historyRounds = ref<GameHistoryEntry[]>([])
  const historyInitialBoard = ref<Board | null>(null)
  const historyIndex = ref<number | null>(null)

  async function fetchHistory() {
    if (!gameId.value) return
    try {
      const history = await getGameHistory(gameId.value)
      historyInitialBoard.value = history.initialBoard
      historyRounds.value = history.rounds
    } catch {
      // Echec silencieux : la navigation dans l'historique est une amelioration de
      // confort, jamais necessaire pour jouer - ne pas bloquer/perturber la partie en
      // cours pour autant.
    }
  }

  /** null(live) <-> n-1 <-> ... <-> -1(depart) - la fleche gauche depuis le direct va au dernier round joue, pas au-dela. */
  function historyPrev() {
    if (historyIndex.value === null) {
      historyIndex.value = historyRounds.value.length - 1
    } else if (historyIndex.value > -1) {
      historyIndex.value -= 1
    }
  }

  /** Symetrique de historyPrev : depasser le dernier round joue revient au direct (null). */
  function historyNext() {
    if (historyIndex.value === null) return
    if (historyIndex.value < historyRounds.value.length - 1) {
      historyIndex.value += 1
    } else {
      historyIndex.value = null
    }
  }

  let subscriptions: StompSubscription[] = []
  /**
   * subscribed/subscribing pilotent le (ré)abonnement : les souscriptions STOMP ne
   * survivent pas à une reconnexion (nouvelle connexion WebSocket sous-jacente), donc
   * une perte puis reprise de connexion doit reproduire exactement le même
   * enchaînement que le join initial (subscribe + publish "view" pour resynchroniser
   * l'état). "subscribing" évite un double abonnement si le listener global de statut
   * se déclenche pendant que joinGame() est déjà en train de s'abonner.
   */
  let subscribed = false
  let subscribing = false

  async function ensureSubscribed() {
    if (subscribed || subscribing || !gameId.value) return
    subscribing = true
    try {
      subscriptions.forEach((sub) => sub.unsubscribe())
      subscriptions = []

      const id = gameId.value
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

      await publish(`/app/games/${id}/view`, { token: token.value })
      subscribed = true
    } catch {
      error.value = {
        code: 'NETWORK_ERROR',
        message: 'Connexion au serveur perdue. Nouvelle tentative automatique...',
      }
    } finally {
      subscribing = false
    }
  }

  // Écoute globale (posée une seule fois, pour toute la durée de vie du store) : dès
  // que la connexion WebSocket revient après une coupure alors qu'une partie est en
  // cours (gameId déjà posé), on reproduit le join pour resynchroniser abonnements et
  // état - exactement ce qu'un rechargement de page ferait déjà.
  onStatusChange((next) => {
    connectionStatus.value = next
    if (next === 'connected') {
      ensureSubscribed()
    } else {
      subscribed = false
    }
  })

  async function joinGame({ gameId: id, token: playerToken = null, color = null }: JoinGameParams) {
    gameId.value = id
    token.value = playerToken
    myColor.value = color
    state.value = null
    error.value = null
    pendingSubmission.value = false
    pendingMove.value = null
    canAct.value = Boolean(playerToken)
    chatMessages.value = []
    historyRounds.value = []
    historyInitialBoard.value = null
    historyIndex.value = null
    subscribed = false

    await ensureSubscribed()
    await fetchHistory()
  }

  /** À appeler en quittant la vue de partie (voir GameView.vue) pour ne pas laisser les abonnements actifs en arrière-plan. */
  function leaveGame() {
    subscriptions.forEach((sub) => sub.unsubscribe())
    subscriptions = []
    subscribed = false
    gameId.value = null
    historyRounds.value = []
    historyInitialBoard.value = null
    historyIndex.value = null
  }

  /**
   * Un round resolu (en direct, ou decouvert au premier chargement) fait grossir
   * roundCount - on refetch alors l'historique detaille, sans jamais faire sauter
   * historyIndex : un round resolu pendant qu'on navigue dans le passe met a jour la
   * liste en silence, sans forcer un retour a la position courante (CLAUDE.md, etape 11).
   */
  watch(
    () => state.value?.roundCount,
    (roundCount, previous) => {
      if (roundCount !== undefined && roundCount !== previous) fetchHistory()
    },
  )

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
    publish(`/app/games/${gameId.value}/move`, { token: token.value, from, to, promotion: promotion ?? null }).catch(() => {
      pendingSubmission.value = false
      pendingMove.value = null
      error.value = { code: 'NETWORK_ERROR', message: "Connexion perdue : réessayez." }
    })
  }

  function submitGuess(from: string | null, to: string | null, promotion: PromotionPieceType | null) {
    pendingSubmission.value = true
    pendingMove.value = from && to ? { from, to } : null
    publish(`/app/games/${gameId.value}/guess`, { token: token.value, from, to, promotion: promotion ?? null }).catch(() => {
      pendingSubmission.value = false
      pendingMove.value = null
      error.value = { code: 'NETWORK_ERROR', message: "Connexion perdue : réessayez." }
    })
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
    connectionStatus,
    canAct,
    chatMessages,
    historyRounds,
    historyInitialBoard,
    historyIndex,
    historyPrev,
    historyNext,
    joinGame,
    leaveGame,
    submitMove,
    submitGuess,
    sendChat,
    dismissError,
  }
})
