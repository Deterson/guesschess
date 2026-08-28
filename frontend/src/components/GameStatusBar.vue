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

const resultText = computed(() => {
  const result = props.state.result
  if (!result) return null
  if (!result.winner) return 'Partie terminée : nulle'
  return `Partie terminée : victoire ${WIN_LABELS[result.winner]}`
})

const resultBgClass = computed(() => {
  const result = props.state.result
  if (!result || !result.winner || !props.myColor) return null
  return result.winner === props.myColor.toUpperCase() ? 'bg-emerald-500' : 'bg-red-500'
})
</script>

<template>
  <div class="mb-4 flex flex-col gap-1 rounded-lg px-4 py-3 text-sm" :class="resultBgClass ?? 'bg-stone-800'">
    <p v-if="!myColor" class="text-stone-400">Vous observez cette partie</p>
    <p v-if="resultText" class="font-semibold text-black">{{ resultText }}</p>
    <p v-else-if="pendingSubmission" class="text-stone-400">En attente de l'adversaire…</p>
    <p v-else-if="myRole === 'mover'"><strong>À vous de jouer</strong></p>
    <p v-else-if="myRole === 'guesser'"><strong>À vous de deviner</strong></p>
    <p v-else>Trait {{ TRAIT_LABELS[state.sideToMove] }}</p>
  </div>
</template>
