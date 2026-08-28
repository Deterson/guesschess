<script setup lang="ts">
import { computed } from 'vue'
import type { Color, GameResultCause, GameStateMessage, GameVariant } from '../types/api'

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

const CAUSE_LABELS: Record<GameResultCause, string> = {
  CHECKMATE: 'échec et mat',
  KING_CAPTURED: 'roi capturé',
  CHECK_PARRY_GUESSED: 'devinette Guessmate (coup parant l\'échec deviné)',
  STALEMATE: 'pat',
  DRAW_FIFTY_MOVE_RULE: 'nulle (règle des 50 coups)',
  DRAW_THREEFOLD_REPETITION: 'nulle (répétition de position)',
  DRAW_INSUFFICIENT_MATERIAL: 'nulle (matériel insuffisant)',
}

const VARIANT_LABELS: Record<GameVariant, string> = { GUESSCHESS: 'Guesschess', GUESSMATE: 'Guessmate' }

const COLOR_LABELS: Record<Color, string> = { WHITE: 'les blancs', BLACK: 'les noirs' }
const TRAIT_LABELS: Record<Color, string> = { WHITE: 'aux blancs', BLACK: 'aux noirs' }
const WIN_LABELS: Record<Color, string> = { WHITE: 'des blancs', BLACK: 'des noirs' }

const resultText = computed(() => {
  const result = props.state.result
  if (!result) return null
  const cause = CAUSE_LABELS[result.cause] ?? result.cause
  if (!result.winner) return `Partie terminée : nulle (${cause})`
  return `Partie terminée : victoire ${WIN_LABELS[result.winner]} (${cause})`
})

const myColorLabel = computed(() => (props.myColor ? COLOR_LABELS[props.myColor.toUpperCase() as Color] : null))
</script>

<template>
  <div class="mb-4 flex flex-col gap-1 rounded-lg bg-stone-800 px-4 py-3 text-sm">
    <p>
      <template v-if="myColor">
        Vous jouez <strong>{{ myColorLabel }}</strong>
      </template>
      <template v-else>Vous observez cette partie</template>
      <span v-if="state.variant" class="text-stone-400">— variante {{ VARIANT_LABELS[state.variant] ?? state.variant }}</span>
    </p>
    <p v-if="resultText" class="font-semibold text-amber-300">{{ resultText }}</p>
    <template v-else>
      <p v-if="myRole">
        Trait {{ TRAIT_LABELS[state.sideToMove] }} — vous êtes
        <strong>{{ myRole === 'mover' ? 'le joueur au trait (choisissez votre coup)' : "l'adversaire (choisissez votre devinette)" }}</strong>
      </p>
      <p v-else>Trait {{ TRAIT_LABELS[state.sideToMove] }}</p>
      <p v-if="pendingSubmission" class="text-stone-400">En attente de l'autre joueur…</p>
    </template>
  </div>
</template>
