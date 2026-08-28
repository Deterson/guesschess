<script setup lang="ts">
import { computed } from 'vue'
import type { Color, RoundSummaryMessage } from '../types/api'

const props = defineProps<{
  round: RoundSummaryMessage
}>()

const emit = defineEmits<{
  dismiss: []
}>()

const COLOR_LABELS: Record<Color, string> = { WHITE: 'les blancs', BLACK: 'les noirs' }
const OF_LABELS: Record<Color, string> = { WHITE: 'des blancs', BLACK: 'des noirs' }

const text = computed(() => {
  const guesser = COLOR_LABELS[props.round.guesser]
  const mover = OF_LABELS[props.round.mover]

  if (props.round.guessedCorrectly) {
    return `${guesser} ont deviné ${props.round.guessedFrom}-${props.round.guessedTo} : coup ${mover} annulé, trait passé au devineur.`
  }
  const guessPart = props.round.guessedFrom
    ? `${guesser} ont deviné ${props.round.guessedFrom}-${props.round.guessedTo}, à tort`
    : `${guesser} n'ont rien deviné`
  return `${guessPart} : coup ${props.round.actualFrom}-${props.round.actualTo} ${mover} joué normalement.`
})
</script>

<template>
  <div class="mb-4 flex items-center justify-between gap-3 rounded-lg bg-emerald-900/60 px-4 py-3 text-sm">
    <p>{{ text }}</p>
    <button type="button" class="shrink-0 text-emerald-300 hover:text-emerald-100" @click="emit('dismiss')">✕</button>
  </div>
</template>
