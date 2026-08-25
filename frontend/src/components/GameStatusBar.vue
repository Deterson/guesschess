<script setup>
import { computed } from 'vue'

const props = defineProps({
  state: { type: Object, required: true },
  myColor: { type: String, required: true },
  myRole: { type: String, required: true },
  pendingSubmission: { type: Boolean, default: false },
})

const CAUSE_LABELS = {
  CHECKMATE: 'échec et mat',
  KING_CAPTURED: 'roi capturé',
  CHECK_PARRY_GUESSED: 'devinette Guessmate (coup parant l\'échec deviné)',
  STALEMATE: 'pat',
  DRAW_FIFTY_MOVE_RULE: 'nulle (règle des 50 coups)',
  DRAW_THREEFOLD_REPETITION: 'nulle (répétition de position)',
  DRAW_INSUFFICIENT_MATERIAL: 'nulle (matériel insuffisant)',
}

const VARIANT_LABELS = { GUESSCHESS: 'Guesschess', GUESSMATE: 'Guessmate' }

const COLOR_LABELS = { WHITE: 'les blancs', BLACK: 'les noirs' }
const TRAIT_LABELS = { WHITE: 'aux blancs', BLACK: 'aux noirs' }
const WIN_LABELS = { WHITE: 'des blancs', BLACK: 'des noirs' }

const resultText = computed(() => {
  const result = props.state.result
  if (!result) return null
  const cause = CAUSE_LABELS[result.cause] ?? result.cause
  if (!result.winner) return `Partie terminée : nulle (${cause})`
  return `Partie terminée : victoire ${WIN_LABELS[result.winner]} (${cause})`
})
</script>

<template>
  <div class="mb-4 flex flex-col gap-1 rounded-lg bg-stone-800 px-4 py-3 text-sm">
    <p>
      Vous jouez <strong>{{ COLOR_LABELS[myColor.toUpperCase()] }}</strong>
      <span v-if="state.variant" class="text-stone-400">— variante {{ VARIANT_LABELS[state.variant] ?? state.variant }}</span>
    </p>
    <p v-if="resultText" class="font-semibold text-amber-300">{{ resultText }}</p>
    <template v-else>
      <p>
        Trait {{ TRAIT_LABELS[state.sideToMove] }} — vous êtes
        <strong>{{ myRole === 'mover' ? 'le joueur au trait (choisissez votre coup)' : "l'adversaire (choisissez votre devinette)" }}</strong>
      </p>
      <p v-if="pendingSubmission" class="text-stone-400">En attente de l'autre joueur…</p>
    </template>
  </div>
</template>
