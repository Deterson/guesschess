<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useGameStore } from '../stores/game'
import { useAuthStore } from '../stores/auth'
import { ApiError, myAccess, joinGame as apiJoinGame } from '../services/api'
import ChessBoard from '../components/ChessBoard.vue'
import PromotionPicker from '../components/PromotionPicker.vue'
import GameStatusBar from '../components/GameStatusBar.vue'
import RoundResultBanner from '../components/RoundResultBanner.vue'
import MoveHistoryList from '../components/MoveHistoryList.vue'
import InviteBanner from '../components/InviteBanner.vue'
import AuthModal from '../components/AuthModal.vue'
import type { PromotionPieceType } from '../types/api'

const props = defineProps<{
  gameId: string
}>()

const gameStore = useGameStore()
const authStore = useAuthStore()
const { state, error, pendingSubmission, pendingMove, myColor, canAct } = storeToRefs(gameStore)

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
    joinError.value = e instanceof ApiError && e.status === 409 ? "Cette partie est déjà complète." : (e as Error).message
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

const myRole = computed(() => {
  if (!state.value || !myColor.value) return null
  return state.value.sideToMove === myColor.value.toUpperCase() ? 'mover' : 'guesser'
})

const boardDisabled = computed(
  () => !state.value || state.value.status === 'FINISHED' || pendingSubmission.value || !canAct.value,
)

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
    <router-link to="/" class="self-start text-sm text-stone-400 hover:text-stone-200">← Accueil</router-link>

    <div v-if="accessDenied" class="text-stone-400">
      Impossible de retrouver votre accès à cette partie.
      <router-link to="/" class="text-emerald-500 hover:text-emerald-400">Retour à l'accueil</router-link>
    </div>

    <div v-else-if="!state" class="text-stone-400">Connexion à la partie…</div>

    <template v-else>
      <div class="grid w-full grid-cols-1 items-start gap-6 @min-[67rem]:grid-cols-[minmax(0,1fr)_36rem_minmax(0,1fr)]">
        <div class="mx-auto w-full max-w-xl @min-[67rem]:mx-0 @min-[67rem]:max-w-none">
          <InviteBanner v-if="showInvite" :game-id="gameId" @dismiss="inviteDismissed = true" />

          <div v-if="myColor && !canAct" class="mb-4 rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
            Vous regardez cette partie en spectateur : quelqu'un d'autre s'est déjà connecté avec ce lien.
          </div>
          <div v-else-if="!myColor && state.full" class="mb-4 rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
            Vous regardez cette partie en spectateur : elle est déjà complète.
          </div>
          <div v-else-if="!myColor" class="mb-4 space-y-2 rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
            <p>Vous regardez cette partie en spectateur.</p>
            <p v-if="joinError" class="text-red-400">{{ joinError }}</p>
            <button
              type="button"
              class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold hover:bg-emerald-500 disabled:opacity-50"
              :disabled="joining"
              @click="startJoin"
            >
              {{ joining ? 'Connexion…' : 'Rejoindre cette partie' }}
            </button>
          </div>

          <GameStatusBar :state="state" :my-color="myColor" :my-role="myRole" :pending-submission="pendingSubmission" />

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

        <div class="mx-auto flex w-full max-w-xl flex-col items-center gap-4 @min-[67rem]:mx-0 @min-[67rem]:max-w-none">
          <ChessBoard
            :board="state.board"
            :legal-moves="state.legalMoves"
            :orientation="myColor ?? 'white'"
            :disabled="boardDisabled"
            :last-round="state.lastRound"
            :pending-move="pendingMove"
            :hover-guess="hoverGuessSquares"
            @choose-move="onChooseMove"
          />

          <button
            v-if="myRole === 'guesser' && !boardDisabled"
            type="button"
            class="rounded-lg bg-stone-700 px-4 py-2 text-sm hover:bg-stone-600"
            @click="submitNoGuess"
          >
            Ne pas deviner
          </button>
        </div>

        <div class="mx-auto w-full max-w-xl @min-[67rem]:mx-0 @min-[67rem]:max-w-none">
          <MoveHistoryList :move-history="state.moveHistory" />
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
    </template>
  </div>
</template>
