<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { GameHistoryEntry } from '../types/api'

const props = defineProps<{
  rounds: GameHistoryEntry[]
  historyIndex: number | null
}>()

const emit = defineEmits<{
  select: [index: number | null]
}>()

const { t } = useI18n()

/**
 * Regroupe les rounds par paire (blancs/noirs) pour l'affichage façon PGGN
 * ("1. e4(e3) e5(Nc6)") - un round par ligne de la liste, mais deux rounds
 * partageant le même moveNumber s'affichent sur la même ligne visuelle.
 */
const movePairs = computed(() => {
  const pairs: { moveNumber: number; white: { entry: GameHistoryEntry; index: number } | null; black: { entry: GameHistoryEntry; index: number } | null }[] = []
  props.rounds.forEach((entry, index) => {
    let pair = pairs.find((p) => p.moveNumber === entry.moveNumber)
    if (!pair) {
      pair = { moveNumber: entry.moveNumber, white: null, black: null }
      pairs.push(pair)
    }
    if (entry.mover === 'WHITE') pair.white = { entry, index }
    else pair.black = { entry, index }
  })
  return pairs
})

/**
 * Notation façon PGGN pour une entrée (voir PggnWriter.renderPly côté backend, même
 * règles d'omission) : pas de parenthèses si aucune devinette, coup réel omis si le
 * round a été annulé (devinette correcte).
 */
function notation(entry: GameHistoryEntry): string {
  if (entry.guessedSan == null) return entry.realSan ?? ''
  if (entry.realSan == null) return `(${entry.guessedSan})`
  return `${entry.realSan}(${entry.guessedSan})`
}

function selectPly(index: number) {
  // Cliquer le tout dernier coup de la liste revient explicitement au direct plutôt
  // que de simplement viser ce même index, qui resterait "en mode navigation".
  emit('select', index === props.rounds.length - 1 ? null : index)
}

/**
 * Le direct (historyIndex null) correspond visuellement au dernier coup de la
 * liste - sans quoi ce dernier coup n'est jamais en surbrillance alors qu'il
 * représente la position actuelle par défaut.
 */
function isActive(index: number): boolean {
  return props.historyIndex === index || (props.historyIndex === null && index === props.rounds.length - 1)
}
</script>

<template>
  <div class="rounded-lg bg-stone-800 px-4 py-3 text-sm">
    <p class="mb-2 font-semibold text-stone-300">{{ t('moveHistory.title') }}</p>
    <p v-if="rounds.length === 0" class="text-stone-500">-</p>
    <ol v-else class="space-y-0.5 text-stone-400">
      <li>
        <button
          type="button"
          class="rounded px-1 hover:bg-stone-700"
          :class="historyIndex === -1 ? 'bg-stone-700 text-stone-100' : ''"
          @click="emit('select', -1)"
        >
          {{ t('moveHistory.startingPosition') }}
        </button>
      </li>
      <li v-for="pair in movePairs" :key="pair.moveNumber" class="flex gap-2">
        <span class="w-6 shrink-0 text-stone-500">{{ pair.moveNumber }}.</span>
        <button
          v-if="pair.white"
          type="button"
          class="rounded px-1 hover:bg-stone-700"
          :class="isActive(pair.white.index) ? 'bg-stone-700 text-stone-100' : ''"
          @click="selectPly(pair.white.index)"
        >
          {{ notation(pair.white.entry) }}
        </button>
        <button
          v-if="pair.black"
          type="button"
          class="rounded px-1 hover:bg-stone-700"
          :class="isActive(pair.black.index) ? 'bg-stone-700 text-stone-100' : ''"
          @click="selectPly(pair.black.index)"
        >
          {{ notation(pair.black.entry) }}
        </button>
      </li>
    </ol>
  </div>
</template>
