<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  board: { type: Array, required: true },
  legalMoves: { type: Array, default: () => [] },
  orientation: { type: String, default: 'white' },
  disabled: { type: Boolean, default: false },
  lastRound: { type: Object, default: null },
})

const emit = defineEmits(['choose-move'])

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']

const GLYPHS = {
  wK: '♔', wQ: '♕', wR: '♖', wB: '♗', wN: '♘', wP: '♙',
  bK: '♚', bQ: '♛', bR: '♜', bB: '♝', bN: '♞', bP: '♟',
}

const selectedFrom = ref(null)

const displayRanks = computed(() => (props.orientation === 'white' ? [7, 6, 5, 4, 3, 2, 1, 0] : [0, 1, 2, 3, 4, 5, 6, 7]))
const displayFiles = computed(() => (props.orientation === 'white' ? [0, 1, 2, 3, 4, 5, 6, 7] : [7, 6, 5, 4, 3, 2, 1, 0]))

const legalFromSquares = computed(() => new Set(props.legalMoves.map((m) => m.from)))
const legalDestinations = computed(() => {
  if (!selectedFrom.value) return new Set()
  return new Set(props.legalMoves.filter((m) => m.from === selectedFrom.value).map((m) => m.to))
})

function algebraic(file, rank) {
  return FILES[file] + (rank + 1)
}

function pieceAt(file, rank) {
  return props.board[rank][file]
}

function glyphOf(code) {
  return code ? GLYPHS[code] : ''
}

function isLastRoundSquare(square) {
  return props.lastRound != null && (square === props.lastRound.actualFrom || square === props.lastRound.actualTo)
}

function onSquareClick(file, rank) {
  if (props.disabled) return
  const square = algebraic(file, rank)

  if (selectedFrom.value && legalDestinations.value.has(square)) {
    const matches = props.legalMoves.filter((m) => m.from === selectedFrom.value && m.to === square)
    const from = selectedFrom.value
    selectedFrom.value = null
    emit('choose-move', { from, to: square, promotionOptions: matches.map((m) => m.promotion) })
    return
  }

  selectedFrom.value = legalFromSquares.value.has(square) ? square : null
}
</script>

<template>
  <div
    class="grid aspect-square w-full max-w-xl grid-cols-8 grid-rows-8 overflow-hidden rounded-lg border-2 border-stone-700 select-none"
  >
    <template v-for="rank in displayRanks" :key="`rank-${rank}`">
      <button
        v-for="file in displayFiles"
        :key="`${file}-${rank}`"
        type="button"
        class="relative flex aspect-square items-center justify-center text-3xl sm:text-4xl"
        :class="[
          (file + rank) % 2 === 0 ? 'bg-amber-800' : 'bg-amber-100',
          selectedFrom === algebraic(file, rank) ? 'ring-4 ring-emerald-400 ring-inset' : '',
          isLastRoundSquare(algebraic(file, rank)) ? 'bg-sky-500/50' : '',
          disabled ? 'cursor-default' : 'cursor-pointer',
        ]"
        :data-square="algebraic(file, rank)"
        :aria-label="algebraic(file, rank) + (pieceAt(file, rank) ? ' ' + pieceAt(file, rank) : ' vide')"
        @click="onSquareClick(file, rank)"
      >
        <span :class="pieceAt(file, rank)?.startsWith('w') ? 'text-white drop-shadow' : 'text-stone-950'">
          {{ glyphOf(pieceAt(file, rank)) }}
        </span>
        <span
          v-if="legalDestinations.has(algebraic(file, rank))"
          class="absolute inset-0 m-auto h-3 w-3 rounded-full bg-emerald-500/70"
        />
      </button>
    </template>
  </div>
</template>
