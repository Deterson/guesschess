<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useGameStore } from '../stores/game'
import { useAuthStore } from '../stores/auth'
import { ApiError, myAccess, joinGame as apiJoinGame } from '../services/api'
import ChessBoard from '../components/ChessBoard.vue'
import PromotionPicker from '../components/PromotionPicker.vue'
import GameStatusBar from '../components/GameStatusBar.vue'
import RoundResultBanner from '../components/RoundResultBanner.vue'
import MoveHistoryList from '../components/MoveHistoryList.vue'
import ChatPanel from '../components/ChatPanel.vue'
import InviteBanner from '../components/InviteBanner.vue'
import AuthModal from '../components/AuthModal.vue'
import LoginModal from '../components/LoginModal.vue'
import PlayerLabel from '../components/PlayerLabel.vue'
import { useClock } from '../composables/useClock'
import type { Board, ColorLower, PieceCode, PromotionPieceType, RoundSummaryMessage } from '../types/api'

const props = defineProps<{
  gameId: string
}>()

const gameStore = useGameStore()
const authStore = useAuthStore()
const router = useRouter()
const { t } = useI18n()
const {
  state,
  error,
  pendingSubmission,
  pendingMove,
  myColor,
  canAct,
  chatMessages,
  connectionStatus,
  historyRounds,
  historyInitialBoard,
  historyIndex,
  players,
} = storeToRefs(gameStore)

onUnmounted(() => {
  gameStore.leaveGame()
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('beforeunload', onBeforeUnload)
  stopTabTitleBlink()
  if (resolvedGuessFlashTimeout) clearTimeout(resolvedGuessFlashTimeout)
  if (connectionLostTimeout) clearTimeout(connectionLostTimeout)
})

/**
 * La bannière "connexion perdue" n'apparaît qu'après un court délai de grâce plutôt
 * qu'instantanément sur connectionStatus !== 'connected' : un onglet réveillé après
 * une mise en veille (retour sur téléphone après plusieurs minutes) se reconnecte en
 * général en une ou deux secondes, et afficher la bannière le temps de ce court
 * aller-retour serait juste un flash inutile plutôt qu'une vraie coupure à signaler.
 */
const CONNECTION_LOST_DELAY_MS = 3000
const showConnectionLostBanner = ref(false)
let connectionLostTimeout: ReturnType<typeof setTimeout> | null = null
watch(
  connectionStatus,
  (status) => {
    if (connectionLostTimeout) {
      clearTimeout(connectionLostTimeout)
      connectionLostTimeout = null
    }
    if (status === 'connected') {
      showConnectionLostBanner.value = false
    } else {
      connectionLostTimeout = setTimeout(() => {
        showConnectionLostBanner.value = true
        connectionLostTimeout = null
      }, CONNECTION_LOST_DELAY_MS)
    }
  },
  { immediate: true },
)

const pendingPromotion = ref<{ from: string; to: string; options: PromotionPieceType[] } | null>(null)
const hoveredGuess = ref(false)
const inviteDismissed = ref(false)

/** Onglet ouvert dans le panneau mobile (<67rem) - voir layout dédié dans le template. */
const mobilePanel = ref<'history' | 'chat' | null>(null)
function toggleMobilePanel(panel: 'history' | 'chat') {
  mobilePanel.value = mobilePanel.value === panel ? null : panel
}
/**
 * Dérivé de l'état plutôt que d'un flag "vient d'être créée" à usage unique
 * (sessionStorage) : reste correct après un rechargement de page (le créateur voit
 * toujours son lien tant que personne ne l'a rejoint), et se masque automatiquement
 * en temps réel dès que l'adversaire rejoint (state.full arrive via la diffusion
 * STOMP déclenchée par GameCreationController.join - GameStateMessage.full).
 */
const showInvite = computed(() => Boolean(myColor.value) && !state.value?.full && !inviteDismissed.value)
const accessDenied = ref(false)
const joining = ref(false)
const joinError = ref<string | null>(null)
const showJoinModal = ref(false)
const showLoginModal = ref(false)

/**
 * Aucun token/couleur dans l'URL (un seul lien par partie, /game/{gameId}) : l'accès
 * se résout par identité (cookie anonyme ou compte JWT) via /api/games/{gameId}/my-access.
 * Mais si HomeView/OAuthCallbackView viennent tout juste de peupler le store pour CE
 * gameId (création ou acceptation d'invitation, juste avant la navigation vers cette
 * page), on le réutilise directement plutôt que de refaire l'aller-retour : le
 * redécouvrir via /my-access dépendrait de la propagation immédiate du cookie anonyme
 * qu'on vient tout juste de poser, pas garantie au tout premier appel d'un navigateur -
 * c'était la cause d'un bug où le créateur se voyait à tort proposer de "rejoindre" sa
 * propre partie, et en cliquant, revendiquait la seule couleur qui devait rester
 * ouverte pour l'adversaire.
 *
 * Tout échec de /my-access AUTRE qu'un GAME_NOT_FOUND confirmé dégrade en spectateur
 * (jamais un blocage) : identité pas liée (NO_ACCESS), mais aussi tout hoquet
 * réseau/CORS ou erreur inattendue - regarder une partie n'a jamais nécessité de
 * résoudre une identité (viewGame côté serveur n'a aucun contrôle d'accès), donc rater
 * CETTE résolution ne doit jamais empêcher la simple lecture. C'était la cause d'un bug
 * où un spectateur pouvait se voir bloqué avec "impossible de retrouver votre accès"
 * alors que la partie était parfaitement consultable.
 */
watch(
  () => props.gameId,
  async (gameId) => {
    if (!gameId) return
    accessDenied.value = false
    if (gameStore.gameId === gameId && gameStore.token) {
      return
    }
    try {
      const found = await myAccess(gameId, authStore.token)
      gameStore.joinGame({ gameId, token: found.token, color: found.color.toLowerCase() as 'white' | 'black' })
    } catch (e) {
      // GAME_NOT_FOUND (confirmé par le serveur : rien à regarder) bloque vraiment.
      // Tout le reste - NO_ACCESS (identité non liée), un hoquet réseau/CORS, une
      // erreur inattendue - dégrade en spectateur plutôt que de bloquer : consulter
      // une partie n'a jamais nécessité de résoudre une identité (viewGame côté
      // serveur ne vérifie aucun jeton), donc un échec de CETTE résolution ne doit
      // jamais empêcher la simple lecture.
      if (e instanceof ApiError && e.code === 'GAME_NOT_FOUND') {
        accessDenied.value = true
      } else {
        gameStore.joinGame({ gameId })
      }
    }
  },
  { immediate: true },
)

/** Étape 16 : pas de chat contre l'ordinateur (aucun interlocuteur côté adverse). */
const isVsComputer = computed(() => players.value?.white?.type === 'COMPUTER' || players.value?.black?.type === 'COMPUTER')

const topPlayer = computed(() => {
  const orientation = myColor.value ?? 'white'
  return orientation === 'white' ? players.value?.black ?? null : players.value?.white ?? null
})
const bottomPlayer = computed(() => {
  const orientation = myColor.value ?? 'white'
  return orientation === 'white' ? players.value?.white ?? null : players.value?.black ?? null
})
const topPlayerColor = computed<ColorLower>(() => ((myColor.value ?? 'white') === 'white' ? 'black' : 'white'))
const bottomPlayerColor = computed<ColorLower>(() => myColor.value ?? 'white')

/**
 * Pendule (étape 12) : une par couleur, dérivée du dernier GameStateMessage (jamais
 * de source de vérité locale, voir useClock). awaitingGuess détecte la phase "le
 * joueur au trait a joué, l'adversaire doit deviner" sans rien révéler de plus que ce
 * que l'état public porte déjà : la pendule tourne pour l'adversaire du joueur au
 * trait (clockRunningFor !== sideToMove) uniquement en mode temps réel.
 */
const whiteClock = useClock(state, 'WHITE')
const blackClock = useClock(state, 'BLACK')
const awaitingGuess = computed(() => {
  const s = state.value
  return Boolean(s?.timeControl) && s?.clockRunningFor != null && s.clockRunningFor !== s.sideToMove
})

function clockPropsFor(color: ColorLower) {
  const clock = color === 'white' ? whiteClock : blackClock
  return {
    clockMs: clock.remainingMs.value,
    clockRunning: clock.running.value,
    urgent: awaitingGuess.value && state.value?.clockRunningFor === color.toUpperCase(),
  }
}

const topClock = computed(() => clockPropsFor(topPlayerColor.value))
const bottomClock = computed(() => clockPropsFor(bottomPlayerColor.value))

function startJoin() {
  joinError.value = null
  if (authStore.isLoggedIn) {
    performJoin(authStore.token)
  } else {
    showJoinModal.value = true
  }
}

function joinAnonymously() {
  showJoinModal.value = false
  performJoin(null)
}

async function performJoin(authToken: string | null) {
  joining.value = true
  joinError.value = null
  try {
    const joined = await apiJoinGame(props.gameId, authToken)
    gameStore.joinGame({ gameId: props.gameId, token: joined.token, color: joined.color.toLowerCase() as 'white' | 'black' })
  } catch (e) {
    joinError.value = e instanceof ApiError && e.status === 409 ? t('game.gameFullError') : (e as Error).message
  } finally {
    joining.value = false
  }
}

const hoverGuessSquares = computed(() => {
  if (!hoveredGuess.value) return null
  const round = state.value?.lastRound
  if (!round?.guessedFrom || !round.guessedTo) return null
  return { from: round.guessedFrom, to: round.guessedTo }
})

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']

function squareToIndices(square: string): [number, number] {
  return [FILES.indexOf(square[0]), Number(square.slice(1)) - 1]
}

/**
 * Deplace une piece sur une copie du plateau - utilise uniquement pour l'apercu client
 * (survol de la devinette) d'un coup non encore/jamais reellement joue, jamais pour le
 * plateau reel qui vient toujours du serveur. Fait aussi suivre la tour en cas de
 * roque (roi qui se deplace de deux cases horizontalement) : sans ca, seul le roi
 * bougeait dans l'apercu (bug corrige).
 */
function applyMoveToBoard(board: Board, from: string, to: string): Board {
  const [fFile, fRank] = squareToIndices(from)
  const [tFile, tRank] = squareToIndices(to)
  const piece = board[fRank]?.[fFile] ?? null
  const next = board.map((row) => row.slice())
  next[fRank][fFile] = null
  next[tRank][tFile] = piece

  if ((piece === 'wK' || piece === 'bK') && fRank === tRank && Math.abs(tFile - fFile) === 2) {
    const kingside = tFile > fFile
    const rookFromFile = kingside ? 7 : 0
    const rookToFile = kingside ? tFile - 1 : tFile + 1
    next[fRank][rookToFile] = next[fRank][rookFromFile]
    next[fRank][rookFromFile] = null
  }

  return next
}

/** Dernier round de l'historique detaille (etape 11) - seul a porter un snapshot de plateau (RoundSummaryMessage n'en a pas). */
const lastHistoryRound = computed(() => historyRounds.value[historyRounds.value.length - 1] ?? null)

/** Plateau juste avant le dernier coup reel joue (round precedent resolu, ou position de depart s'il n'y en a qu'un). */
const boardBeforeLastRound = computed<Board | null>(() => {
  const n = historyRounds.value.length
  if (n === 0) return null
  if (n === 1) return historyInitialBoard.value
  return historyRounds.value[n - 2]?.boardAfter ?? null
})

/**
 * Au survol de RoundResultBanner : montre le plateau comme si le dernier coup reel
 * n'avait pas ete joue et que la devinette avait ete jouee a sa place, plutot que de
 * superposer un ghost sur le coup reel toujours affiche (source de confusion : les
 * deux coups semblaient joues en meme temps).
 */
const hoverGuessBoard = computed<Board | null>(() => {
  if (!hoveredGuess.value) return null
  const round = lastHistoryRound.value
  const base = boardBeforeLastRound.value
  if (!round?.guessedFrom || !round.guessedTo || !base) return null
  return applyMoveToBoard(base, round.guessedFrom, round.guessedTo)
})

/** Roi en echec : uniquement en direct sur le plateau reel (jamais en navigation historique ni pendant l'apercu au survol d'une devinette). */
const checkedColor = computed<ColorLower | null>(() => {
  if (historyIndex.value !== null || hoveredGuess.value || !state.value?.inCheck) return null
  return state.value.sideToMove.toLowerCase() as ColorLower
})

const myRole = computed(() => {
  if (!state.value || !myColor.value) return null
  return state.value.sideToMove === myColor.value.toUpperCase() ? 'mover' : 'guesser'
})

/** Halo blanc "à qui de jouer" : bas si le joueur au trait est en bas du plateau affiché (nous, ou blancs pour un spectateur), haut sinon. Masqué tant que la partie n'a pas commencé/est finie, sideToMove n'ayant alors rien de significatif à indiquer. */
const turnIndicator = computed<'top' | 'bottom' | null>(() => {
  if (!state.value?.full || state.value.status === 'FINISHED') return null
  return state.value.sideToMove.toLowerCase() === bottomPlayerColor.value ? 'bottom' : 'top'
})

/**
 * Coup deviné affiché en fondu (~1s) juste après la résolution d'un round - uniquement
 * pour le joueur qui a joué le coup réel de CE round (round.mover), jamais pour le
 * devineur (qui connaît déjà sa propre devinette) ni un spectateur, et seulement si la
 * devinette diffère du coup réellement joué (sinon rien de nouveau à montrer). La pièce
 * est résolue sur le plateau tel qu'il était juste AVANT la résolution (dernier
 * GameStateMessage reçu avant celui-ci), pas via historyRounds qui n'est rafraîchi
 * qu'après un aller-retour REST asynchrone (voir stores/game.ts) et arriverait donc
 * trop tard pour déclencher l'animation de façon fiable.
 */
const resolvedGuessFlash = ref<{ from: string; to: string; piece: PieceCode; id: number } | null>(null)
let resolvedGuessFlashTimeout: ReturnType<typeof setTimeout> | null = null
let resolvedGuessFlashCounter = 0

watch(
  () => state.value,
  (newState, oldState) => {
    if (!newState || !oldState || !myColor.value) return
    if (newState.roundCount === oldState.roundCount) return
    const round = newState.lastRound
    if (!round?.guessedFrom || !round.guessedTo) return
    if (round.guessedFrom === round.actualFrom && round.guessedTo === round.actualTo) return
    if (round.mover !== myColor.value.toUpperCase()) return
    const piece = pieceAtSquareOf(oldState.board, round.guessedFrom)
    if (!piece) return

    resolvedGuessFlashCounter += 1
    resolvedGuessFlash.value = { from: round.guessedFrom, to: round.guessedTo, piece, id: resolvedGuessFlashCounter }
    if (resolvedGuessFlashTimeout) clearTimeout(resolvedGuessFlashTimeout)
    resolvedGuessFlashTimeout = setTimeout(() => {
      resolvedGuessFlash.value = null
    }, 1000)
  },
)

/** Masqué en navigation historique : les cases resteraient les mêmes mais le plateau affiché ne correspondrait plus au round qui vient de se résoudre. */
const displayResolvedGuessFlash = computed(() => (historyIndex.value === null ? resolvedGuessFlash.value : null))

/**
 * awaitingGuess (public, calculé plus haut) pilote le clignotement des DEUX pendules -
 * visible de tous, dérivé d'un état déjà public. Le halo autour du plateau et du
 * message de statut, lui, ne doit clignoter que pour le joueur qui doit effectivement
 * deviner (pas son adversaire, pas un spectateur) - awaitingGuessMine restreint donc à
 * myRole === 'guesser'.
 */
const awaitingGuessMine = computed(() => awaitingGuess.value && myRole.value === 'guesser')

/**
 * Onglet du navigateur : titre changé en "à vous de jouer/deviner" et clignotant,
 * uniquement quand c'est notre tour (rôle déterminé, pas déjà soumis) ET que
 * l'onglet n'est pas actif - inutile de le signaler si le joueur regarde déjà
 * l'écran de statut du jeu.
 */
const BASE_TAB_TITLE = 'Guesschess'
const isTabVisible = ref(!document.hidden)
let tabTitleBlinkInterval: ReturnType<typeof setInterval> | null = null
let tabTitleBlinkOn = false

function onVisibilityChange() {
  isTabVisible.value = !document.hidden
}

const myTurnTabLabel = computed(() => {
  if (
    !myRole.value ||
    pendingSubmission.value ||
    !canAct.value ||
    !state.value?.full ||
    state.value.status === 'FINISHED'
  ) {
    return null
  }
  return myRole.value === 'mover' ? t('game.tabTitleYourTurnToPlay') : t('game.tabTitleYourTurnToGuess')
})

function stopTabTitleBlink() {
  if (tabTitleBlinkInterval !== null) {
    clearInterval(tabTitleBlinkInterval)
    tabTitleBlinkInterval = null
  }
  document.title = BASE_TAB_TITLE
}

function startTabTitleBlink(label: string) {
  tabTitleBlinkOn = true
  document.title = label
  tabTitleBlinkInterval = setInterval(() => {
    tabTitleBlinkOn = !tabTitleBlinkOn
    document.title = tabTitleBlinkOn ? label : BASE_TAB_TITLE
  }, 1000)
}

watch(
  [myTurnTabLabel, isTabVisible],
  ([label, visible]) => {
    stopTabTitleBlink()
    if (label && !visible) startTabTitleBlink(label)
  },
  { immediate: true },
)

const drawOfferedByMe = computed(
  () => Boolean(state.value?.drawOfferedBy) && myColor.value != null && state.value?.drawOfferedBy === myColor.value.toUpperCase(),
)
const drawOfferedByOpponent = computed(
  () => Boolean(state.value?.drawOfferedBy) && myColor.value != null && state.value?.drawOfferedBy !== myColor.value.toUpperCase(),
)

function onDrawButtonClick() {
  if (drawOfferedByOpponent.value) {
    gameStore.respondToDraw(true)
  } else {
    gameStore.offerDraw()
  }
}

const rematchOfferedByMe = computed(
  () =>
    Boolean(state.value?.rematchOfferedBy) && myColor.value != null && state.value?.rematchOfferedBy === myColor.value.toUpperCase(),
)
const rematchOfferedByOpponent = computed(
  () =>
    Boolean(state.value?.rematchOfferedBy) && myColor.value != null && state.value?.rematchOfferedBy !== myColor.value.toUpperCase(),
)
/** Par defaut true (jamais grise) tant que la presence n'est pas encore connue - voir PlayerLabel/GamePresenceService. */
const opponentConnected = computed(() => {
  if (!myColor.value) return true
  const opponentInfo = myColor.value === 'white' ? players.value?.black : players.value?.white
  return opponentInfo?.connected ?? true
})

function onRematchButtonClick() {
  gameStore.offerRematch()
}

/** Des que les deux couleurs ont propose la revanche, le serveur cree la nouvelle partie et diffuse son id - on y navigue directement. */
watch(
  () => state.value?.rematchGameId,
  (rematchGameId) => {
    if (rematchGameId) router.push(`/game/${rematchGameId}`)
  },
)

/**
 * pendingSubmission ne desactive plus le plateau : tant que l'adversaire n'a pas
 * soumis (donc tant que le round n'est pas resolu), le joueur peut reselectionner un
 * autre coup/devinette a tout moment - chaque nouvelle soumission remplace la
 * precedente cote serveur (voir Game.submitMove/submitGuess). Reste valable en partie
 * chronometree (etape 12) : la pendule concernee n'est arretee/demarree qu'a la toute
 * premiere soumission d'un round, les suivantes sont neutres pour elle (voir
 * Game.stopClockFor/startClockFor cote backend).
 */
const boardDisabled = computed(
  () =>
    !state.value ||
    state.value.status === 'FINISHED' ||
    !state.value.full ||
    !canAct.value ||
    historyIndex.value !== null,
)

/** Avertissement natif du navigateur (façon lichess) uniquement pour une partie en cours en mode temps réel : pas de pendule à perdre en correspondance. */
const shouldWarnBeforeUnload = computed(
  () => Boolean(state.value?.timeControl) && state.value?.status === 'ONGOING' && Boolean(state.value?.full),
)

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!shouldWarnBeforeUnload.value) return
  event.preventDefault()
  event.returnValue = ''
}

/**
 * Round de l'historique actuellement affiché (null en direct, ou en dehors des
 * bornes de historyRounds - jamais le cas normalement, garde défensive).
 */
const viewedRound = computed(() => {
  if (historyIndex.value === null || historyIndex.value < 0) return null
  return historyRounds.value[historyIndex.value] ?? null
})

/** Plateau affiché : en direct par défaut, sinon dérivé de l'historique (étape 11). */
const displayBoard = computed<Board | null>(() => {
  if (historyIndex.value === null) return state.value?.board ?? null
  if (historyIndex.value === -1) return historyInitialBoard.value
  // boardAfter ne vaut null que pour le round terminal Guessmate : le plateau live
  // reflète déjà cette position finale (partie FINISHED), pas la peine d'en garder
  // une copie séparée côté backend pour ce seul cas.
  return viewedRound.value?.boardAfter ?? state.value?.board ?? null
})

/** Plateau juste avant le round consulté (pour retrouver la piece devinee - voir displayGhost). */
const boardBeforeViewedRound = computed<Board | null>(() => {
  const index = historyIndex.value
  if (index === null || index < 0) return null
  return index === 0 ? historyInitialBoard.value : (historyRounds.value[index - 1]?.boardAfter ?? null)
})

function pieceAtSquareOf(board: Board, square: string) {
  const [file, rank] = squareToIndices(square)
  return board[rank]?.[file] ?? null
}

/**
 * Coup deviné du round consulté en navigation historique - null en direct (voir
 * hoverGuessSquares pour le survol souris). La piece est resolue sur le plateau
 * D'AVANT le round (pas boardAfter, qui reflete le coup REEL deja joue et peut donc
 * avoir deplace cette meme piece ailleurs si guessedFrom === actualFrom).
 */
const displayGhost = computed(() => {
  const round = viewedRound.value
  if (!round?.guessedFrom || !round.guessedTo) return null
  const base = boardBeforeViewedRound.value
  const piece = base ? pieceAtSquareOf(base, round.guessedFrom) : null
  if (!piece) return null
  return { from: round.guessedFrom, to: round.guessedTo, piece }
})

/** Réutilise le surlignage sky/rouge déjà géré par ChessBoard pour le round consulté en navigation. */
const displayLastRound = computed<RoundSummaryMessage | null>(() => {
  if (historyIndex.value === null) return state.value?.lastRound ?? null
  const round = viewedRound.value
  if (!round) return null
  return {
    mover: round.mover,
    guesser: round.guesser,
    actualFrom: round.actualFrom,
    actualTo: round.actualTo,
    guessedFrom: round.guessedFrom,
    guessedTo: round.guessedTo,
    guessedCorrectly: round.guessedCorrectly,
  }
})

function onHistorySelect(index: number | null) {
  historyIndex.value = index
}

function onKeydown(event: KeyboardEvent) {
  const target = document.activeElement
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) return
  if (event.key === 'ArrowLeft') gameStore.historyPrev()
  else if (event.key === 'ArrowRight') gameStore.historyNext()
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('beforeunload', onBeforeUnload)
})

function submitChosenMove({ from, to, promotion }: { from: string; to: string; promotion: PromotionPieceType | null }) {
  if (myRole.value === 'mover') {
    gameStore.submitMove(from, to, promotion)
  } else {
    gameStore.submitGuess(from, to, promotion)
  }
}

function onChooseMove({
  from,
  to,
  promotionOptions,
}: {
  from: string
  to: string
  promotionOptions: (PromotionPieceType | null)[]
}) {
  const nonNullOptions = promotionOptions.filter((option): option is PromotionPieceType => option !== null)
  if (nonNullOptions.length > 1) {
    pendingPromotion.value = { from, to, options: nonNullOptions }
    return
  }
  submitChosenMove({ from, to, promotion: nonNullOptions[0] ?? null })
}

function onPromotionSelected(promotion: PromotionPieceType) {
  if (!pendingPromotion.value) return
  submitChosenMove({ from: pendingPromotion.value.from, to: pendingPromotion.value.to, promotion })
  pendingPromotion.value = null
}

</script>

<template>
  <div
    class="@container mx-auto flex w-full max-w-7xl flex-col items-center gap-4 px-4 py-8 max-[67rem]:h-full max-[67rem]:min-h-0 max-[67rem]:gap-0 max-[67rem]:overflow-hidden max-[67rem]:px-2 max-[67rem]:py-2"
  >
    <div v-if="accessDenied" class="text-stone-400">
      {{ t('game.accessDenied') }}
      <router-link to="/" class="text-emerald-500 hover:text-emerald-400">{{ t('common.backToHome') }}</router-link>
    </div>

    <div v-else-if="!state" class="text-stone-400">{{ t('common.connecting') }}</div>

    <template v-else>
      <!-- Layout desktop (>=67rem) : historique/chat visibles en colonnes, inchangé. -->
      <div class="hidden w-full grid-cols-1 items-start gap-6 @min-[67rem]:grid @min-[67rem]:grid-cols-[minmax(0,1fr)_36rem_minmax(0,1fr)]">
        <!--
          "contents" en etroit desimbrique ce wrapper : statut et chat redeviennent
          des items de grille independants (au meme titre que board/historique),
          reordonnables individuellement via order-* pour que le chat suive le
          plateau plutot que le statut dans l'empilement grid-cols-1. "block" en
          large le re-imbrique : statut+chat s'empilent alors en flux normal DANS ce
          wrapper (devenu la colonne 1), immunise contre le probleme des lignes CSS
          Grid (une ligne prend la hauteur de son membre le plus grand, ici le
          plateau) qui repoussait sinon le chat tout en bas de la colonne au lieu de
          le laisser suivre immediatement le texte de statut.
        -->
        <div class="contents @min-[67rem]:flex @min-[67rem]:h-full @min-[67rem]:flex-col @min-[67rem]:col-start-1">
          <div class="order-1 mx-auto w-full max-w-xl @min-[67rem]:order-none @min-[67rem]:mx-0 @min-[67rem]:max-w-none">
            <InviteBanner v-if="showInvite" :game-id="gameId" @dismiss="inviteDismissed = true" />

            <div v-if="myColor && !canAct" class="mb-4 rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
              {{ t('game.spectatorGeneric') }}
            </div>
            <div v-else-if="!myColor && state.full" class="mb-4 rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
              {{ t('game.spectatorGeneric') }}
            </div>
            <div v-else-if="!myColor" class="mb-4 space-y-2 rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
              <p>{{ t('game.spectatorGeneric') }}</p>
              <p v-if="joinError" class="text-red-400">{{ joinError }}</p>
              <button
                type="button"
                class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold hover:bg-emerald-500 disabled:opacity-50"
                :disabled="joining"
                @click="startJoin"
              >
                {{ joining ? t('common.connecting') : t('game.joinButton') }}
              </button>
            </div>

            <div v-if="showConnectionLostBanner" class="mb-4 rounded-lg bg-amber-900/60 px-4 py-3 text-sm text-amber-100">
              {{ t('game.connectionLostReconnecting') }}
            </div>

            <RoundResultBanner
              v-if="state.lastRound"
              :round="state.lastRound"
              :my-color="myColor"
              @hover="hoveredGuess = $event"
            />

            <div v-if="error" class="mb-4 rounded-lg bg-red-900/60 px-4 py-3 text-sm">
              {{ error.message }}
              <button type="button" class="ml-2 text-red-300 hover:text-red-100" @click="gameStore.dismissError()">✕</button>
            </div>
          </div>

          <div
            v-if="!isVsComputer"
            class="order-3 mx-auto w-full max-w-xl @min-[67rem]:order-none @min-[67rem]:mx-0 @min-[67rem]:min-h-0 @min-[67rem]:max-w-none @min-[67rem]:flex-1"
          >
            <ChatPanel :messages="chatMessages" :can-send="Boolean(myColor) && canAct" @send="gameStore.sendChat" />
          </div>
        </div>

        <div class="order-2 mx-auto flex w-full max-w-xl flex-col items-center gap-4 @min-[67rem]:order-none @min-[67rem]:col-start-2 @min-[67rem]:mx-0 @min-[67rem]:max-w-none">
          <PlayerLabel
            class="w-full"
            :color="topPlayerColor"
            :info="topPlayer"
            :clock-ms="topClock.clockMs"
            :clock-running="topClock.clockRunning"
            :urgent="topClock.urgent"
          />

          <ChessBoard
            :board="hoverGuessBoard ?? displayBoard ?? state.board"
            :legal-moves="state.legalMoves"
            :orientation="myColor ?? 'white'"
            :disabled="boardDisabled"
            :last-round="displayLastRound"
            :pending-move="pendingMove"
            :hover-guess="hoverGuessSquares"
            :ghost-move="displayGhost"
            :resolved-guess-flash="displayResolvedGuessFlash"
            :checked-color="checkedColor"
            :awaiting-guess="awaitingGuessMine"
            :turn-indicator="historyIndex === null ? turnIndicator : null"
            @choose-move="onChooseMove"
          />

          <PlayerLabel
            class="w-full"
            :color="bottomPlayerColor"
            :info="bottomPlayer"
            :clock-ms="bottomClock.clockMs"
            :clock-running="bottomClock.clockRunning"
            :urgent="bottomClock.urgent"
          />

          <p v-if="myColor && !authStore.isLoggedIn" class="-mt-3 text-center text-xs text-stone-500">
            <button type="button" class="underline hover:text-stone-400" @click="showLoginModal = true">
              {{ t('game.anonymousAccessReminderLink') }}
            </button>
            {{ t('game.anonymousAccessReminderSuffix') }}
          </p>

          <GameStatusBar
            class="w-full"
            :state="state"
            :my-color="myColor"
            :my-role="myRole"
            :pending-submission="pendingSubmission"
            :awaiting-guess="awaitingGuessMine"
          />
        </div>

        <div class="order-4 mx-auto w-full max-w-xl @min-[67rem]:order-none @min-[67rem]:col-start-3 @min-[67rem]:mx-0 @min-[67rem]:max-w-none">
          <MoveHistoryList :rounds="historyRounds" :history-index="historyIndex" @select="onHistorySelect" />

          <div v-if="myColor && canAct && state.full" class="mt-2 flex flex-col items-center">
            <p v-if="drawOfferedByOpponent" class="mb-1 text-xs text-stone-400">{{ t('game.opponentOffersDraw') }}</p>
            <button
              type="button"
              class="rounded-lg px-4 py-2 text-sm disabled:opacity-50"
              :class="drawOfferedByOpponent ? 'bg-violet-700 hover:bg-violet-600' : 'bg-stone-700 hover:bg-stone-600'"
              :disabled="drawOfferedByMe || state.status === 'FINISHED'"
              @click="onDrawButtonClick"
            >
              {{ drawOfferedByOpponent ? t('game.acceptDraw') : t('game.offerDraw') }}
            </button>
          </div>

          <div v-if="myColor && canAct && state.status === 'FINISHED'" class="mt-2 flex flex-col items-center">
            <p v-if="rematchOfferedByOpponent" class="mb-1 text-xs text-stone-400">{{ t('game.opponentOffersRematch') }}</p>
            <button
              type="button"
              class="rounded-lg px-4 py-2 text-sm disabled:opacity-50"
              :class="rematchOfferedByOpponent ? 'bg-violet-700 hover:bg-violet-600' : 'bg-stone-700 hover:bg-stone-600'"
              :disabled="rematchOfferedByMe || !opponentConnected"
              @click="onRematchButtonClick"
            >
              {{ rematchOfferedByOpponent ? t('game.acceptRematch') : t('game.offerRematch') }}
            </button>
          </div>
        </div>
      </div>

      <!--
        Layout mobile (<67rem) : page à hauteur fixe, sans scroll de page (voir App.vue
        qui borne la hauteur et masque le footer sur cette route). Glisser le doigt sur
        le plateau ne doit jamais pouvoir être interprété comme un scroll de page.
        Historique et chat ne sont plus affichés en continu : un onglet en bas les ouvre
        dans un panneau qui prend l'espace flexible restant (lui peut défiler en
        interne), à la manière d'un intercalaire de classeur.
      -->
      <div class="flex h-full min-h-0 w-full flex-col @min-[67rem]:hidden">
        <div class="w-full shrink-0">
          <InviteBanner v-if="showInvite" :game-id="gameId" @dismiss="inviteDismissed = true" />

          <div v-if="myColor && !canAct" class="mb-2 rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
            {{ t('game.spectatorGeneric') }}
          </div>
          <div v-else-if="!myColor && state.full" class="mb-2 rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
            {{ t('game.spectatorGeneric') }}
          </div>
          <div v-else-if="!myColor" class="mb-2 space-y-2 rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
            <p>{{ t('game.spectatorGeneric') }}</p>
            <p v-if="joinError" class="text-red-400">{{ joinError }}</p>
            <button
              type="button"
              class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold hover:bg-emerald-500 disabled:opacity-50"
              :disabled="joining"
              @click="startJoin"
            >
              {{ joining ? t('common.connecting') : t('game.joinButton') }}
            </button>
          </div>

          <div v-if="showConnectionLostBanner" class="mb-2 rounded-lg bg-amber-900/60 px-4 py-3 text-sm text-amber-100">
            {{ t('game.connectionLostReconnecting') }}
          </div>

          <div v-if="error" class="mb-2 rounded-lg bg-red-900/60 px-4 py-3 text-sm">
            {{ error.message }}
            <button type="button" class="ml-2 text-red-300 hover:text-red-100" @click="gameStore.dismissError()">✕</button>
          </div>
        </div>

        <PlayerLabel
          class="w-full shrink-0"
          :color="topPlayerColor"
          :info="topPlayer"
          :clock-ms="topClock.clockMs"
          :clock-running="topClock.clockRunning"
          :urgent="topClock.urgent"
        />

        <div class="mx-auto min-h-0 max-w-full flex-1 aspect-square">
          <ChessBoard
            class="h-full"
            :board="hoverGuessBoard ?? displayBoard ?? state.board"
            :legal-moves="state.legalMoves"
            :orientation="myColor ?? 'white'"
            :disabled="boardDisabled"
            :last-round="displayLastRound"
            :pending-move="pendingMove"
            :hover-guess="hoverGuessSquares"
            :ghost-move="displayGhost"
            :resolved-guess-flash="displayResolvedGuessFlash"
            :checked-color="checkedColor"
            :awaiting-guess="awaitingGuessMine"
            :turn-indicator="historyIndex === null ? turnIndicator : null"
            @choose-move="onChooseMove"
          />
        </div>

        <PlayerLabel
          class="w-full shrink-0"
          :color="bottomPlayerColor"
          :info="bottomPlayer"
          :clock-ms="bottomClock.clockMs"
          :clock-running="bottomClock.clockRunning"
          :urgent="bottomClock.urgent"
        />

        <p v-if="myColor && !authStore.isLoggedIn" class="shrink-0 py-1 text-center text-xs text-stone-500">
          <button type="button" class="underline hover:text-stone-400" @click="showLoginModal = true">
            {{ t('game.anonymousAccessReminderLink') }}
          </button>
          {{ t('game.anonymousAccessReminderSuffix') }}
        </p>

        <!-- Infobulles fusionnées : devinette (1/3) + statut (2/3), ou statut seul si pas de round. -->
        <div class="flex w-full shrink-0 items-stretch gap-2">
          <div v-if="state.lastRound" class="w-1/3">
            <RoundResultBanner :round="state.lastRound" :my-color="myColor" @hover="hoveredGuess = $event" />
          </div>
          <div :class="state.lastRound ? 'w-2/3' : 'w-full'">
            <GameStatusBar
              :state="state"
              :my-color="myColor"
              :my-role="myRole"
              :pending-submission="pendingSubmission"
              :awaiting-guess="awaitingGuessMine"
            />
          </div>
        </div>

        <template v-if="myColor && canAct && state.full">
          <div v-if="state.status !== 'FINISHED'" class="flex shrink-0 flex-col items-center gap-1 pb-2">
            <p v-if="drawOfferedByOpponent" class="text-xs text-stone-400">{{ t('game.opponentOffersDraw') }}</p>
            <div class="flex items-center gap-3">
              <button
                type="button"
                class="flex h-11 w-11 items-center justify-center rounded-lg bg-stone-800 text-stone-300 hover:bg-stone-700"
                :aria-label="t('game.resign')"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="h-5 w-5" aria-hidden="true">
                  <line x1="5" y1="3" x2="5" y2="21" />
                  <path d="M5 4h13l-3.5 4.5L18 13H5" />
                </svg>
              </button>
              <button
                type="button"
                class="flex h-11 w-11 items-center justify-center rounded-lg disabled:opacity-50"
                :class="drawOfferedByOpponent ? 'bg-violet-700 hover:bg-violet-600' : 'bg-stone-800 hover:bg-stone-700'"
                :disabled="drawOfferedByMe"
                :aria-label="drawOfferedByOpponent ? t('game.acceptDraw') : t('game.offerDraw')"
                @click="onDrawButtonClick"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" class="h-5 w-5" aria-hidden="true">
                  <g transform="rotate(-22 12 12)"><rect x="2" y="10" width="10" height="4" rx="2" /></g>
                  <g transform="rotate(22 12 12)"><rect x="12" y="10" width="10" height="4" rx="2" /></g>
                </svg>
              </button>
            </div>
          </div>

          <div v-else class="flex shrink-0 flex-col items-center gap-1 pb-2">
            <p v-if="rematchOfferedByOpponent" class="text-xs text-stone-400">{{ t('game.opponentOffersRematch') }}</p>
            <button
              type="button"
              class="rounded-lg px-4 py-2 text-sm disabled:opacity-50"
              :class="rematchOfferedByOpponent ? 'bg-violet-700 hover:bg-violet-600' : 'bg-stone-700 hover:bg-stone-600'"
              :disabled="rematchOfferedByMe || !opponentConnected"
              @click="onRematchButtonClick"
            >
              {{ rematchOfferedByOpponent ? t('game.acceptRematch') : t('game.offerRematch') }}
            </button>
          </div>
        </template>

        <div
          class="w-full"
          :class="mobilePanel ? 'min-h-0 flex-1 overflow-y-auto' : 'h-0 shrink-0 overflow-hidden'"
        >
          <MoveHistoryList v-if="mobilePanel === 'history'" :rounds="historyRounds" :history-index="historyIndex" @select="onHistorySelect" />
          <ChatPanel
            v-if="mobilePanel === 'chat' && !isVsComputer"
            class="h-full"
            :messages="chatMessages"
            :can-send="Boolean(myColor) && canAct"
            @send="gameStore.sendChat"
          />
        </div>

        <div class="flex shrink-0 border-t border-stone-800">
          <button
            type="button"
            class="flex flex-1 items-center justify-center py-3"
            :class="mobilePanel === 'history' ? 'bg-stone-800 text-emerald-400' : 'text-stone-400'"
            :aria-label="t('moveHistory.title')"
            :aria-pressed="mobilePanel === 'history'"
            @click="toggleMobilePanel('history')"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="h-6 w-6" aria-hidden="true">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 7v5l3.5 2" />
            </svg>
          </button>
          <button
            v-if="!isVsComputer"
            type="button"
            class="flex flex-1 items-center justify-center border-l border-stone-800 py-3"
            :class="mobilePanel === 'chat' ? 'bg-stone-800 text-emerald-400' : 'text-stone-400'"
            :aria-label="t('chat.title')"
            :aria-pressed="mobilePanel === 'chat'"
            @click="toggleMobilePanel('chat')"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="h-6 w-6" aria-hidden="true">
              <path d="M4 5h16v11H8l-4 4V5z" />
            </svg>
          </button>
        </div>
      </div>

      <PromotionPicker
        v-if="pendingPromotion"
        :color="myColor ?? 'white'"
        :options="pendingPromotion.options"
        @select="onPromotionSelected"
      />

      <AuthModal
        :open="showJoinModal"
        :pending-action="{ type: 'join', gameId }"
        @anonymous="joinAnonymously"
        @close="showJoinModal = false"
      />

      <LoginModal :open="showLoginModal" :return-to="`/game/${gameId}`" :in-game="true" @close="showLoginModal = false" />
    </template>
  </div>
</template>
