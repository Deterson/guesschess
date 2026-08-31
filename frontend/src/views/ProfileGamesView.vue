<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { listMyGames } from '../services/api'
import ChessBoard from '../components/ChessBoard.vue'
import type { GameSummaryHttpResponse } from '../types/api'

const PAGE_SIZE = 20

const authStore = useAuthStore()
const { t } = useI18n()
const games = ref<GameSummaryHttpResponse[]>([])
const page = ref(0)
const hasMore = ref(true)
const loading = ref(false)
const error = ref<string | null>(null)

const OUTCOME_ROW_CLASS: Record<GameSummaryHttpResponse['outcome'], string> = {
  WON: 'bg-emerald-900/30 hover:bg-emerald-900/50',
  LOST: 'bg-red-900/30 hover:bg-red-900/50',
  DRAW: 'bg-stone-700/30 hover:bg-stone-700/50',
  ONGOING: 'bg-stone-900 hover:bg-stone-800',
}

async function loadMore() {
  loading.value = true
  error.value = null
  try {
    const next = await listMyGames(page.value, PAGE_SIZE, authStore.token!)
    games.value.push(...next)
    hasMore.value = next.length === PAGE_SIZE
    page.value += 1
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

onMounted(loadMore)
</script>

<template>
  <div class="space-y-3 lg:w-1/2">
    <h2 class="text-lg font-semibold">{{ t('profile.myGames') }}</h2>

    <p v-if="error" class="text-sm text-red-400">{{ error }}</p>
    <p v-else-if="!loading && games.length === 0" class="text-sm text-stone-500">{{ t('profile.noGamesYet') }}</p>

    <ul class="space-y-2">
      <li v-for="game in games" :key="game.gameId">
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
              {{ game.opponentName ?? t('profile.waitingOpponent') }}
            </p>
            <p class="text-xs text-stone-400">
              {{ game.myColor === 'WHITE' ? t('common.white') : t('common.black') }}
            </p>
          </div>
        </router-link>
      </li>
    </ul>

    <button
      v-if="hasMore"
      type="button"
      class="w-full rounded-lg bg-stone-800 px-4 py-2 text-sm hover:bg-stone-700 disabled:opacity-50"
      :disabled="loading"
      @click="loadMore"
    >
      {{ loading ? t('profile.loading') : t('profile.loadMore') }}
    </button>
  </div>
</template>
