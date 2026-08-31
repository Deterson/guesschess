<script setup lang="ts">
import { computed } from 'vue'
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

const TRAIT_LABELS: Record<Color, string> = { WHITE: 'aux blancs', BLACK: 'aux noirs' }
const WIN_LABELS: Record<Color, string> = { WHITE: 'des blancs', BLACK: 'des noirs' }

const isGuessRepetitionDraw = computed(() => props.state.result != null && props.state.result.cause === 'DRAW_THREE_GUESS_REPETITION')

const resultText = computed(() => {
  const result = props.state.result
  if (!result || isGuessRepetitionDraw.value) return null
  if (!result.winner) return 'Partie terminée : nulle'
  return `Partie terminée : victoire ${WIN_LABELS[result.winner]}`
})

const resultBgClass = computed(() => {
  const result = props.state.result
  if (!result) return null
  if (!result.winner) return 'bg-yellow-400'
  if (!props.myColor) return null
  return result.winner === props.myColor.toUpperCase() ? 'bg-emerald-500' : 'bg-red-500'
})
</script>

<template>
  <div class="mb-4 flex flex-col gap-1 rounded-lg px-4 py-3 text-sm" :class="resultBgClass ?? 'bg-stone-800'">
    <p v-if="!myColor" class="text-stone-400">Vous observez cette partie</p>
    <p v-if="isGuessRepetitionDraw" class="font-semibold text-black">
      Partie terminée : nulle (<router-link to="/how-to-play" class="text-black underline hover:no-underline">6 guess repetition</router-link>)
    </p>
    <p v-else-if="resultText" class="font-semibold text-black">{{ resultText }}</p>
    <p v-else-if="myColor && !state.full" class="text-stone-400">En attente d'un adversaire pour commencer à jouer…</p>
    <p v-else-if="pendingSubmission" class="text-stone-400">En attente de l'adversaire…</p>
    <p v-else-if="myRole === 'mover'"><strong>À vous de jouer</strong></p>
    <p v-else-if="myRole === 'guesser'"><strong>À vous de deviner</strong></p>
    <p v-else>Trait {{ TRAIT_LABELS[state.sideToMove] }}</p>
  </div>
</template>
