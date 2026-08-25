<script setup>
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useGameStore } from '../stores/game'
import ChessBoard from '../components/ChessBoard.vue'
import PromotionPicker from '../components/PromotionPicker.vue'
import GameStatusBar from '../components/GameStatusBar.vue'
import RoundResultBanner from '../components/RoundResultBanner.vue'
import MoveHistoryList from '../components/MoveHistoryList.vue'

const props = defineProps({
  gameId: { type: String, required: true },
  token: { type: String, default: null },
  color: { type: String, default: null },
})

const gameStore = useGameStore()
const { state, error, pendingSubmission, myColor } = storeToRefs(gameStore)

const pendingPromotion = ref(null)
const showLastRound = ref(true)

watch(
  () => [props.gameId, props.token, props.color],
  ([gameId, token, color]) => {
    if (!gameId || !token || !color) return
    showLastRound.value = false
    gameStore.joinGame({ gameId, token, color })
  },
  { immediate: true },
)

watch(
  () => state.value?.lastRound,
  (lastRound) => {
    if (lastRound) showLastRound.value = true
  },
)

const myRole = computed(() => {
  if (!state.value || !myColor.value) return null
  return state.value.sideToMove === myColor.value.toUpperCase() ? 'mover' : 'guesser'
})

const boardDisabled = computed(
  () => !state.value || state.value.status === 'FINISHED' || pendingSubmission.value,
)

function submitChosenMove({ from, to, promotion }) {
  if (myRole.value === 'mover') {
    gameStore.submitMove(from, to, promotion)
  } else {
    gameStore.submitGuess(from, to, promotion)
  }
}

function onChooseMove({ from, to, promotionOptions }) {
  const nonNullOptions = promotionOptions.filter((option) => option !== null)
  if (nonNullOptions.length > 1) {
    pendingPromotion.value = { from, to, options: nonNullOptions }
    return
  }
  submitChosenMove({ from, to, promotion: nonNullOptions[0] ?? null })
}

function onPromotionSelected(promotion) {
  submitChosenMove({ from: pendingPromotion.value.from, to: pendingPromotion.value.to, promotion })
  pendingPromotion.value = null
}

function submitNoGuess() {
  gameStore.submitGuess(null, null, null)
}
</script>

<template>
  <div class="mx-auto flex max-w-4xl flex-col items-center gap-4 px-4 py-8">
    <router-link to="/" class="self-start text-sm text-stone-400 hover:text-stone-200">← Accueil</router-link>

    <div v-if="!state" class="text-stone-400">Connexion à la partie…</div>

    <template v-else>
      <div class="w-full max-w-xl">
        <GameStatusBar :state="state" :my-color="color" :my-role="myRole" :pending-submission="pendingSubmission" />

        <RoundResultBanner
          v-if="state.lastRound && showLastRound"
          :round="state.lastRound"
          @dismiss="showLastRound = false"
        />

        <div v-if="error" class="mb-4 rounded-lg bg-red-900/60 px-4 py-3 text-sm">
          {{ error.message }}
          <button type="button" class="ml-2 text-red-300 hover:text-red-100" @click="gameStore.dismissError()">✕</button>
        </div>
      </div>

      <ChessBoard
        :board="state.board"
        :legal-moves="state.legalMoves"
        :orientation="color"
        :disabled="boardDisabled"
        :last-round="state.lastRound"
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

      <div class="w-full max-w-xl">
        <MoveHistoryList :move-history="state.moveHistory" />
      </div>

      <PromotionPicker
        v-if="pendingPromotion"
        :color="color"
        :options="pendingPromotion.options"
        @select="onPromotionSelected"
      />
    </template>
  </div>
</template>
