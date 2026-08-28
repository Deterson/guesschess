<script setup lang="ts">
import { computed } from 'vue'
import type { ColorLower, RoundSummaryMessage } from '../types/api'

const props = withDefaults(
  defineProps<{
    round: RoundSummaryMessage
    myColor?: ColorLower | null
  }>(),
  {
    myColor: null,
  },
)

const emit = defineEmits<{
  hover: [boolean]
}>()

const text = computed(() => {
  if (!props.round.guessedFrom || !props.round.guessedTo) return 'pas de devinette'
  return `coup deviné : ${props.round.guessedFrom} (${props.round.guessedCorrectly ? 'bingo' : 'raté'})`
})

const colorClass = computed(() => {
  if (!props.round.guessedCorrectly) return 'bg-sky-900/60'
  const guesserIsMe = props.myColor != null && props.round.guesser === props.myColor.toUpperCase()
  return guesserIsMe ? 'bg-emerald-900/60' : 'bg-red-900/60'
})
</script>

<template>
  <div
    class="mb-4 rounded-lg px-4 py-3 text-sm"
    :class="colorClass"
    @mouseenter="emit('hover', true)"
    @mouseleave="emit('hover', false)"
  >
    <p>{{ text }}</p>
  </div>
</template>
