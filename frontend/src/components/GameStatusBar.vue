<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Color, GameStateMessage } from '../types/api'

const props = withDefaults(
  defineProps<{
    state: GameStateMessage
    myColor?: string | null
    myRole?: 'mover' | 'guesser' | null
    pendingSubmission?: boolean
  }>(),
  {
    myColor: null,
    myRole: null,
    pendingSubmission: false,
  },
)

const { t } = useI18n()

const TRAIT_LABELS = computed<Record<Color, string>>(() => ({ WHITE: t('gameStatusBar.traitWhite'), BLACK: t('gameStatusBar.traitBlack') }))
const WIN_LABELS = computed<Record<Color, string>>(() => ({ WHITE: t('gameStatusBar.winnerWhite'), BLACK: t('gameStatusBar.winnerBlack') }))

const isGuessRepetitionDraw = computed(() => props.state.result != null && props.state.result.cause === 'DRAW_THREE_GUESS_REPETITION')
const isGuessmate = computed(() => props.state.result != null && props.state.result.cause === 'CHECK_PARRY_GUESSED')

const guessmateWinnerLabel = computed(() => {
  const result = props.state.result
  if (!result || !result.winner) return null
  return WIN_LABELS.value[result.winner]
})

const resultText = computed(() => {
  const result = props.state.result
  if (!result || isGuessRepetitionDraw.value || isGuessmate.value) return null
  if (!result.winner) return t('gameStatusBar.resultDraw')
  return t('gameStatusBar.resultWin', { winner: WIN_LABELS.value[result.winner] })
})

const resultBgClass = computed(() => {
  const result = props.state.result
  if (!result) return null
  if (!result.winner) return 'bg-yellow-400'
  if (!props.myColor) return null
  return result.winner === props.myColor.toUpperCase() ? 'bg-emerald-500' : 'bg-red-500'
})

const isMyTurn = computed(
  () =>
    !props.state.result &&
    props.myColor != null &&
    props.state.full &&
    !props.pendingSubmission &&
    (props.myRole === 'mover' || props.myRole === 'guesser'),
)
</script>

<template>
  <div
    class="mb-4 flex flex-col items-center gap-1 rounded-lg px-4 py-3 text-center text-sm"
    :class="resultBgClass ?? (isMyTurn ? 'animate-breathe' : 'bg-stone-800')"
  >
    <p v-if="!myColor" class="text-stone-400">{{ t('gameStatusBar.spectating') }}</p>
    <i18n-t v-if="isGuessRepetitionDraw" keypath="gameStatusBar.resultDrawGuessRepetition" tag="p" class="font-semibold text-black">
      <template #link>
        <router-link to="/how-to-play#six-guess-repetition" class="text-black underline hover:no-underline">{{ t('gameStatusBar.guessRepetitionLinkText') }}</router-link>
      </template>
    </i18n-t>
    <i18n-t v-else-if="isGuessmate" keypath="gameStatusBar.resultWinGuessmate" tag="p" class="font-semibold text-black">
      <template #link>
        <router-link to="/how-to-play#guessmate" class="text-black underline hover:no-underline">{{ t('gameStatusBar.guessmateLinkText') }}</router-link>
      </template>
      <template #winner>{{ guessmateWinnerLabel }}</template>
    </i18n-t>
    <p v-else-if="resultText" class="font-semibold text-black">{{ resultText }}</p>
    <p v-else-if="myColor && !state.full" class="text-stone-400">{{ t('gameStatusBar.waitingOpponentToStart') }}</p>
    <p v-else-if="pendingSubmission" class="text-stone-400">{{ t('gameStatusBar.waitingOpponentSubmission') }}</p>
    <p v-else-if="myRole === 'mover'"><strong>{{ t('gameStatusBar.yourTurnToPlay') }}</strong></p>
    <p v-else-if="myRole === 'guesser'"><strong>{{ t('gameStatusBar.yourTurnToGuess') }}</strong></p>
    <p v-else>{{ t('gameStatusBar.sideToMove', { side: TRAIT_LABELS[state.sideToMove] }) }}</p>
  </div>
</template>

<style scoped>
.animate-breathe {
  animation: breathe 3000ms ease-in-out infinite;
}

@keyframes breathe {
  0%,
  50% {
    background-color: #292524;
  }
  80% {
    background-color: #381e3b;
  }
  100% {
    background-color: #292524;
  }
}
</style>
