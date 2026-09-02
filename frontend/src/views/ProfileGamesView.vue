<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { listMyGames } from '../services/api'
import GameSummaryList from '../components/GameSummaryList.vue'
import type { GameSummaryHttpResponse } from '../types/api'

const PAGE_SIZE = 20

const authStore = useAuthStore()
const { t } = useI18n()
const games = ref<GameSummaryHttpResponse[]>([])
const page = ref(0)
const hasMore = ref(true)
const loading = ref(false)
const error = ref<string | null>(null)

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
  <div class="lg:w-1/2">
    <h2 class="mb-3 text-lg font-semibold">{{ t('profile.myGames') }}</h2>
    <GameSummaryList :games="games" :has-more="hasMore" :loading="loading" :error="error" @load-more="loadMore" />
  </div>
</template>
