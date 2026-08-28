<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { Board, ColorLower, LegalMoveMessage, PieceCode, PromotionPieceType, RoundSummaryMessage } from '../types/api'

import wK from '../assets/pieces/wK.svg'
import wQ from '../assets/pieces/wQ.svg'
import wR from '../assets/pieces/wR.svg'
import wB from '../assets/pieces/wB.svg'
import wN from '../assets/pieces/wN.svg'
import wP from '../assets/pieces/wP.svg'
import bK from '../assets/pieces/bK.svg'
import bQ from '../assets/pieces/bQ.svg'
import bR from '../assets/pieces/bR.svg'
import bB from '../assets/pieces/bB.svg'
import bN from '../assets/pieces/bN.svg'
import bP from '../assets/pieces/bP.svg'

const props = withDefaults(
  defineProps<{
    board: Board
    legalMoves?: LegalMoveMessage[]
    orientation?: ColorLower
    disabled?: boolean
    lastRound?: RoundSummaryMessage | null
  }>(),
  {
    legalMoves: () => [],
    orientation: 'white',
    disabled: false,
    lastRound: null,
  },
)

const emit = defineEmits<{
  'choose-move': [{ from: string; to: string; promotionOptions: (PromotionPieceType | null)[] }]
}>()

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']

const PIECE_ICONS: Record<PieceCode, string> = {
  wK, wQ, wR, wB, wN, wP,
  bK, bQ, bR, bB, bN, bP,
}

const selectedFrom = ref<string | null>(null)
const boardRef = ref<HTMLElement | null>(null)
const squareSize = ref(64)
let resizeObserver: ResizeObserver | null = null

const DRAG_THRESHOLD_PX = 4

interface DragState {
  pointerId: number
  from: string
  piece: PieceCode
  startX: number
  startY: number
  x: number
  y: number
  dragging: boolean
}

const dragState = ref<DragState | null>(null)
let suppressNextClick = false

onMounted(() => {
  if (boardRef.value) {
    resizeObserver = new ResizeObserver(() => {
      if (boardRef.value) squareSize.value = boardRef.value.clientWidth / 8
    })
    resizeObserver.observe(boardRef.value)
    squareSize.value = boardRef.value.clientWidth / 8
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})

const displayRanks = computed(() => (props.orientation === 'white' ? [7, 6, 5, 4, 3, 2, 1, 0] : [0, 1, 2, 3, 4, 5, 6, 7]))
const displayFiles = computed(() => (props.orientation === 'white' ? [0, 1, 2, 3, 4, 5, 6, 7] : [7, 6, 5, 4, 3, 2, 1, 0]))

const legalFromSquares = computed(() => new Set(props.legalMoves.map((m) => m.from)))
const legalDestinations = computed(() => {
  if (!selectedFrom.value) return new Set<string>()
  return new Set(props.legalMoves.filter((m) => m.from === selectedFrom.value).map((m) => m.to))
})

function algebraic(file: number, rank: number): string {
  return FILES[file] + (rank + 1)
}

function pieceAt(file: number, rank: number): PieceCode | null {
  return props.board[rank][file]
}

function iconOf(code: PieceCode | null): string | null {
  return code ? PIECE_ICONS[code] : null
}

function isLastRoundSquare(square: string): boolean {
  return props.lastRound != null && (square === props.lastRound.actualFrom || square === props.lastRound.actualTo)
}

function attemptMove(from: string, to: string): boolean {
  if (!legalDestinations.value.has(to)) return false
  const matches = props.legalMoves.filter((m) => m.from === from && m.to === to)
  emit('choose-move', { from, to, promotionOptions: matches.map((m) => m.promotion) })
  return true
}

function onSquareClick(file: number, rank: number) {
  if (props.disabled) return
  if (suppressNextClick) {
    suppressNextClick = false
    return
  }
  const square = algebraic(file, rank)

  if (selectedFrom.value && attemptMove(selectedFrom.value, square)) {
    selectedFrom.value = null
    return
  }

  selectedFrom.value = legalFromSquares.value.has(square) ? square : null
}

function onSquarePointerDown(event: PointerEvent, file: number, rank: number) {
  if (props.disabled || event.button !== 0) return
  const square = algebraic(file, rank)
  const piece = pieceAt(file, rank)
  if (!piece || !legalFromSquares.value.has(square)) return

  selectedFrom.value = square
  dragState.value = {
    pointerId: event.pointerId,
    from: square,
    piece,
    startX: event.clientX,
    startY: event.clientY,
    x: event.clientX,
    y: event.clientY,
    dragging: false,
  }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onSquarePointerMove(event: PointerEvent) {
  const drag = dragState.value
  if (!drag || event.pointerId !== drag.pointerId) return

  drag.x = event.clientX
  drag.y = event.clientY
  if (!drag.dragging) {
    const dx = event.clientX - drag.startX
    const dy = event.clientY - drag.startY
    if (Math.hypot(dx, dy) > DRAG_THRESHOLD_PX) drag.dragging = true
  }
}

function onSquarePointerUp(event: PointerEvent) {
  const drag = dragState.value
  if (!drag || event.pointerId !== drag.pointerId) return

  if (drag.dragging) {
    suppressNextClick = true
    const targetEl = document.elementFromPoint(event.clientX, event.clientY)
    const targetSquare = targetEl?.closest<HTMLElement>('[data-square]')?.dataset.square ?? null
    if (targetSquare) attemptMove(drag.from, targetSquare)
    selectedFrom.value = null
  }

  dragState.value = null
}

function onSquarePointerCancel(event: PointerEvent) {
  if (dragState.value?.pointerId === event.pointerId) dragState.value = null
}
</script>

<template>
  <div
    ref="boardRef"
    class="relative grid aspect-square w-full max-w-xl touch-none grid-cols-8 grid-rows-8 overflow-hidden rounded-lg border-2 border-stone-700 select-none"
  >
    <template v-for="rank in displayRanks" :key="`rank-${rank}`">
      <button
        v-for="file in displayFiles"
        :key="`${file}-${rank}`"
        type="button"
        class="relative flex aspect-square items-center justify-center"
        :class="[
          (file + rank) % 2 === 0 ? 'bg-amber-800' : 'bg-amber-100',
          selectedFrom === algebraic(file, rank) ? 'ring-4 ring-emerald-400 ring-inset' : '',
          isLastRoundSquare(algebraic(file, rank)) ? 'bg-sky-500/50' : '',
          disabled ? 'cursor-default' : 'cursor-pointer',
        ]"
        :data-square="algebraic(file, rank)"
        :aria-label="algebraic(file, rank) + (pieceAt(file, rank) ? ' ' + pieceAt(file, rank) : ' vide')"
        @click="onSquareClick(file, rank)"
        @pointerdown="onSquarePointerDown($event, file, rank)"
        @pointermove="onSquarePointerMove"
        @pointerup="onSquarePointerUp"
        @pointercancel="onSquarePointerCancel"
      >
        <img
          v-if="iconOf(pieceAt(file, rank)) && !(dragState?.dragging && dragState.from === algebraic(file, rank))"
          :src="iconOf(pieceAt(file, rank))!"
          class="h-[80%] w-[80%] drop-shadow"
          draggable="false"
          alt=""
        />
        <span
          v-if="legalDestinations.has(algebraic(file, rank))"
          class="absolute inset-0 m-auto h-3 w-3 rounded-full bg-emerald-500/70"
        />
      </button>
    </template>

    <img
      v-if="dragState?.dragging"
      :src="iconOf(dragState.piece)!"
      class="pointer-events-none fixed z-50 opacity-60 drop-shadow-lg"
      :style="{
        left: dragState.x + 'px',
        top: dragState.y + 'px',
        width: squareSize * 0.8 + 'px',
        height: squareSize * 0.8 + 'px',
        transform: 'translate(-50%, -50%)',
      }"
      draggable="false"
      alt=""
    />
  </div>
</template>
