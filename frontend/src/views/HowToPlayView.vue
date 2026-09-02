<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ChessBoard from '../components/ChessBoard.vue'
import type { Board, BoardCell, PieceCode, RoundSummaryMessage } from '../types/api'

const { t } = useI18n()

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']

function emptyBoard(): Board {
  return Array.from({ length: 8 }, () => Array<BoardCell>(8).fill(null))
}

const START_BOARD: Board = [
  ['wR', 'wN', 'wB', 'wQ', 'wK', 'wB', 'wN', 'wR'],
  Array<BoardCell>(8).fill('wP'),
  Array<BoardCell>(8).fill(null),
  Array<BoardCell>(8).fill(null),
  Array<BoardCell>(8).fill(null),
  Array<BoardCell>(8).fill(null),
  Array<BoardCell>(8).fill('bP'),
  ['bR', 'bN', 'bB', 'bQ', 'bK', 'bB', 'bN', 'bR'],
]

/** Copie `base` en appliquant `changes` (case algébrique -> pièce ou null pour vider). */
function withChanges(base: Board, changes: Record<string, PieceCode | null>): Board {
  const board = base.map((row) => [...row])
  for (const [square, piece] of Object.entries(changes)) {
    const file = FILES.indexOf(square[0])
    const rank = Number(square[1]) - 1
    board[rank][file] = piece
  }
  return board
}

// Exemple 1 : devinette incorrecte - le coup réel (Cf3) est joué normalement.
const example1Board = withChanges(START_BOARD, { g1: null, f3: 'wN' })
const example1LastRound: RoundSummaryMessage = {
  mover: 'WHITE',
  guesser: 'BLACK',
  actualFrom: 'g1',
  actualTo: 'f3',
  guessedFrom: 'e2',
  guessedTo: 'e4',
  guessedCorrectly: false,
}
const example1Guess = { from: 'e2', to: 'e4' }

// Exemple 2 : devinette correcte - le coup (e4) est annulé, le pion reste sur place.
const example2Board = START_BOARD
const example2LastRound: RoundSummaryMessage = {
  mover: 'WHITE',
  guesser: 'BLACK',
  actualFrom: 'e2',
  actualTo: 'e4',
  guessedFrom: 'e2',
  guessedTo: 'e4',
  guessedCorrectly: true,
}
const example2Guess = { from: 'e2', to: 'e4' }

// Exemple 3 : Guessmate - les blancs sont en échec, le coup annulé (Rf2) n'est donc
// jamais réellement joué sur le plateau : le roi reste en échec sur sa case de départ,
// la partie se termine directement (voir Game.java, cas GUESSCHESS + moverWasInCheck).
const example3Board = withChanges(emptyBoard(), { e1: 'wK', e8: 'bR', g8: 'bK' })
const example3LastRound: RoundSummaryMessage = {
  mover: 'WHITE',
  guesser: 'BLACK',
  actualFrom: 'e1',
  actualTo: 'f2',
  guessedFrom: 'e1',
  guessedTo: 'f2',
  guessedCorrectly: true,
}
const example3Guess = { from: 'e1', to: 'f2' }
</script>

<template>
  <div class="relative mx-auto max-w-4xl px-4 py-12">
    <!--
      L'encart PGGN ne doit jamais déplacer la colonne centrale (toujours centrée sur
      l'écran, indépendamment de sa présence) : à partir de l'écran assez large pour
      accueillir les deux sans chevauchement, il sort donc du flux (absolute) et
      s'accroche par son bord droit à la gauche de la colonne (right-full), pour ne
      grandir que vers la gauche si son contenu en a besoin. Le seuil (min-[1560px])
      est calculé pour que la colonne (max-w-4xl = 896px) laisse, de chaque côté, au
      moins la largeur de l'encart (w-75 = 300px) + sa marge (mr-8 = 32px) ; en dessous,
      l'encart reste dans le flux normal et s'affiche au-dessus de la colonne (mobile
      compris). `h-full` sur cet encart lui donne la même hauteur que la colonne centrale
      (qui définit la hauteur du conteneur relatif), ce qui laisse de la place au
      `sticky` interne pour rester visible pendant le défilement sans déborder sous le
      dernier exemple.
    -->
    <aside class="mb-8 min-[1560px]:absolute min-[1560px]:top-0 min-[1560px]:right-full min-[1560px]:mr-8 min-[1560px]:mb-0 min-[1560px]:h-full min-[1560px]:w-75">
      <div class="rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300 min-[1560px]:sticky min-[1560px]:top-8">
        <p class="mb-2 font-semibold text-stone-200">{{ t('howToPlay.pggnTitle') }}</p>
        <p class="text-stone-400">{{ t('howToPlay.pggnText') }}</p>
      </div>
    </aside>

    <div class="flex flex-col gap-10">
      <div class="flex flex-col gap-3 text-center">
        <h1 class="text-3xl font-bold">{{ t('howToPlay.title') }}</h1>
        <p class="text-stone-400">{{ t('howToPlay.intro') }}</p>
      </div>

      <div class="rounded-lg bg-stone-800 px-4 py-3 text-sm text-stone-300">
        <p class="mb-2 font-semibold text-stone-200">{{ t('howToPlay.legendTitle') }}</p>
        <ul class="flex flex-col gap-1.5">
          <li class="flex items-center gap-2">
            <span class="h-4 w-4 shrink-0 rounded ring-4 ring-violet-400 ring-inset"></span>
            {{ t('howToPlay.legendGuess') }}
          </li>
          <li class="flex items-center gap-2">
            <span class="h-4 w-4 shrink-0 rounded bg-sky-500/50"></span>
            {{ t('howToPlay.legendPlayed') }}
          </li>
          <li class="flex items-center gap-2">
            <span class="h-4 w-4 shrink-0 rounded bg-red-500/50"></span>
            {{ t('howToPlay.legendCancelled') }}
          </li>
        </ul>
      </div>

      <section class="flex flex-col gap-4">
        <div class="text-center">
          <h2 class="text-xl font-semibold">{{ t('howToPlay.example1Title') }}</h2>
          <p class="mt-1 text-sm text-stone-400">{{ t('howToPlay.example1Text') }}</p>
        </div>
        <div class="mx-auto w-full max-w-sm">
          <ChessBoard :board="example1Board" :last-round="example1LastRound" :hover-guess="example1Guess" disabled />
          <p class="mt-2 text-center font-mono text-sm text-stone-400">Nf3(e4)</p>
        </div>
      </section>

      <section class="flex flex-col gap-4">
        <div class="text-center">
          <h2 class="text-xl font-semibold">{{ t('howToPlay.example2Title') }}</h2>
          <p class="mt-1 text-sm text-stone-400">{{ t('howToPlay.example2Text') }}</p>
        </div>
        <div class="mx-auto w-full max-w-sm">
          <ChessBoard :board="example2Board" :last-round="example2LastRound" :hover-guess="example2Guess" disabled />
          <p class="mt-2 text-center font-mono text-sm text-stone-400">(e4)</p>
        </div>
      </section>

      <section id="guessmate" class="flex flex-col gap-4 scroll-mt-8">
        <div class="text-center">
          <h2 class="text-xl font-semibold text-red-400">{{ t('howToPlay.example3Title') }}</h2>
          <p class="mt-1 text-sm text-stone-400">{{ t('howToPlay.example3Text') }}</p>
        </div>
        <div class="mx-auto w-full max-w-sm">
          <ChessBoard :board="example3Board" :last-round="example3LastRound" :hover-guess="example3Guess" disabled />
          <p class="mt-2 text-center font-mono text-sm text-stone-400">(Kf2)#</p>
          <p class="mt-2 text-center text-sm text-gray-500">{{ t('howToPlay.example3Caption') }}</p>
        </div>
      </section>

      <section id="six-guess-repetition" class="flex flex-col gap-4 scroll-mt-8">
        <div class="text-center">
          <h2 class="text-xl font-semibold">{{ t('howToPlay.guessRepetitionTitle') }}</h2>
          <p class="mt-1 text-sm text-stone-400">{{ t('howToPlay.guessRepetitionText') }}</p>
        </div>
      </section>
    </div>
  </div>
</template>
