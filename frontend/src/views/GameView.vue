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
import type { Board, ColorLower, PromotionPieceType, RoundSummaryMessage } from '../types/api'

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
  stopTabTitleBlink()
})

const pendingPromotion = ref<{ from: string; to: string; options: PromotionPieceType[] } | null>(null)
const hoveredGuess = ref(false)
const inviteDismissed = ref(false)
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

function applyMoveToBoard(board: Board, from: string, to: string): Board {
  const [fFile, fRank] = squareToIndices(from)
  const [tFile, tRank] = squareToIndices(to)
  const piece = board[fRank]?.[fFile] ?? null
  const next = board.map((row) => row.slice())
  next[fRank][fFile] = null
  next[tRank][tFile] = piece
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
 * precedente cote serveur (voir Game.submitMove/submitGuess). Cette possibilite n'a
 * de sens que hors contexte chronometre - a restreindre une fois le controle du temps
 * modelise (etape 12 de la roadmap).
 */
const boardDisabled = computed(
  () =>
    !state.value ||
    state.value.status === 'FINISHED' ||
    !state.value.full ||
    !canAct.value ||
    historyIndex.value !== null,
)

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

function submitNoGuess() {
  gameStore.submitGuess(null, null, null)
}
</script>

<template>
  <div class="@container mx-auto flex w-full max-w-7xl flex-col items-center gap-4 px-4 py-8">
    <router-link to="/" class="self-start text-sm text-stone-400 hover:text-stone-200">{{ t('game.backHome') }}</router-link>

    <div v-if="accessDenied" class="text-stone-400">
      {{ t('game.accessDenied') }}
      <router-link to="/" class="text-emerald-500 hover:text-emerald-400">{{ t('common.backToHome') }}</router-link>
    </div>

    <div v-else-if="!state" class="text-stone-400">{{ t('common.connecting') }}</div>

    <template v-else>
      <div class="grid w-full grid-cols-1 items-start gap-6 @min-[67rem]:grid-cols-[minmax(0,1fr)_36rem_minmax(0,1fr)]">
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
        <div class="contents @min-[67rem]:block @min-[67rem]:col-start-1">
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

            <div v-if="connectionStatus !== 'connected'" class="mb-4 rounded-lg bg-amber-900/60 px-4 py-3 text-sm text-amber-100">
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

          <div class="order-3 mx-auto w-full max-w-xl @min-[67rem]:order-none @min-[67rem]:mx-0 @min-[67rem]:max-w-none">
            <ChatPanel :messages="chatMessages" :can-send="Boolean(myColor) && canAct" @send="gameStore.sendChat" />

            <p v-if="myColor && !authStore.isLoggedIn" class="mt-2 text-center text-xs text-stone-500">
              <button type="button" class="underline hover:text-stone-400" @click="showLoginModal = true">
                {{ t('game.anonymousAccessReminderLink') }}
              </button>
              {{ t('game.anonymousAccessReminderSuffix') }}
            </p>
          </div>
        </div>

        <div class="order-2 mx-auto flex w-full max-w-xl flex-col items-center gap-4 @min-[67rem]:order-none @min-[67rem]:col-start-2 @min-[67rem]:mx-0 @min-[67rem]:max-w-none">
          <PlayerLabel class="w-full" :color="topPlayerColor" :info="topPlayer" />

          <ChessBoard
            :board="hoverGuessBoard ?? displayBoard ?? state.board"
            :legal-moves="state.legalMoves"
            :orientation="myColor ?? 'white'"
            :disabled="boardDisabled"
            :last-round="displayLastRound"
            :pending-move="pendingMove"
            :hover-guess="hoverGuessSquares"
            :ghost-move="displayGhost"
            :checked-color="checkedColor"
            @choose-move="onChooseMove"
          />

          <PlayerLabel class="w-full" :color="bottomPlayerColor" :info="bottomPlayer" />

          <GameStatusBar class="w-full" :state="state" :my-color="myColor" :my-role="myRole" :pending-submission="pendingSubmission" />

          <button
            v-if="myRole === 'guesser' && !boardDisabled"
            type="button"
            class="rounded-lg bg-stone-700 px-4 py-2 text-sm hover:bg-stone-600"
            @click="submitNoGuess"
          >
            {{ t('game.dontGuess') }}
          </button>
        </div>

        <div class="order-4 mx-auto w-full max-w-xl @min-[67rem]:order-none @min-[67rem]:col-start-3 @min-[67rem]:mx-0 @min-[67rem]:max-w-none">
          <MoveHistoryList :rounds="historyRounds" :history-index="historyIndex" @select="onHistorySelect" />

          <div v-if="myColor && canAct" class="mt-2 flex flex-col items-center">
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
