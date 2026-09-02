<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ChessBoard from './ChessBoard.vue'
import type { GameSummaryHttpResponse } from '../types/api'

/**
 * Liste de parties partagee entre "Mes parties" (ProfileGamesView) et le profil public
 * d'un joueur (PublicProfileView) - meme rendu (miniature + adversaire + couleur),
 * seule la source des donnees (listMyGames vs listGamesByLogin) differe entre les deux.
 */
const props = defineProps<{
  games: GameSummaryHttpResponse[]
  hasMore: boolean
  loading: boolean
  error: string | null
}>()

defineEmits<{ loadMore: [] }>()

const { t } = useI18n()

const OUTCOME_ROW_CLASS: Record<GameSummaryHttpResponse['outcome'], string> = {
  WON: 'bg-emerald-900/30 hover:bg-emerald-900/50',
  LOST: 'bg-red-900/30 hover:bg-red-900/50',
  DRAW: 'bg-stone-700/30 hover:bg-stone-700/50',
  ONGOING: 'bg-stone-900 hover:bg-stone-800',
}

function opponentLabel(game: GameSummaryHttpResponse): string {
  switch (game.opponentType) {
    case 'ACCOUNT':
      return game.opponentName ?? ''
    case 'ANONYMOUS':
      return t('profile.anonymousOpponent')
    default:
      return t('profile.waitingOpponent')
  }
}
</script>

<template>
  <div class="space-y-3">
    <p v-if="props.error" class="text-sm text-red-400">{{ props.error }}</p>
    <p v-else-if="!props.loading && props.games.length === 0" class="text-sm text-stone-500">{{ t('profile.noGamesYet') }}</p>

    <ul class="space-y-2">
      <li v-for="game in props.games" :key="game.gameId">
        <router-link
          :to="`/game/${game.gameId}`"
          class="flex items-center gap-4 rounded-lg p-3 transition-colors"
          :class="OUTCOME_ROW_CLASS[game.outcome]"
        >
          <div class="h-28 w-28 shrink-0 overflow-hidden rounded">
            <ChessBoard :board="game.board" :orientation="game.myColor.toLowerCase() as 'white' | 'black'" disabled />
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium">
              {{ opponentLabel(game) }}
            </p>
            <p class="text-xs text-stone-400">
              {{ game.myColor === 'WHITE' ? t('common.white') : t('common.black') }}
            </p>
          </div>
        </router-link>
      </li>
    </ul>

    <button
      v-if="props.hasMore"
      type="button"
      class="w-full rounded-lg bg-stone-800 px-4 py-2 text-sm hover:bg-stone-700 disabled:opacity-50"
      :disabled="props.loading"
      @click="$emit('loadMore')"
    >
      {{ props.loading ? t('profile.loading') : t('profile.loadMore') }}
    </button>
  </div>
</template>
