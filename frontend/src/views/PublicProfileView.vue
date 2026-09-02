<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError, getPublicProfile, listGamesByLogin } from '../services/api'
import GameSummaryList from '../components/GameSummaryList.vue'
import type { GameSummaryHttpResponse, PublicProfileHttpResponse } from '../types/api'

const PAGE_SIZE = 20

const props = defineProps<{ login: string }>()

const { t } = useI18n()

const profile = ref<PublicProfileHttpResponse | null>(null)
const notFound = ref(false)
const loadingProfile = ref(true)

const games = ref<GameSummaryHttpResponse[]>([])
const page = ref(0)
const hasMore = ref(true)
const loadingGames = ref(false)
const gamesError = ref<string | null>(null)

async function loadProfile(login: string) {
  profile.value = null
  notFound.value = false
  loadingProfile.value = true
  games.value = []
  page.value = 0
  hasMore.value = true
  gamesError.value = null
  try {
    profile.value = await getPublicProfile(login)
    await loadMoreGames()
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      notFound.value = true
    } else {
      gamesError.value = (e as Error).message
    }
  } finally {
    loadingProfile.value = false
  }
}

async function loadMoreGames() {
  if (!profile.value) return
  loadingGames.value = true
  gamesError.value = null
  try {
    const next = await listGamesByLogin(profile.value.login, page.value, PAGE_SIZE)
    games.value.push(...next)
    hasMore.value = next.length === PAGE_SIZE
    page.value += 1
  } catch (e) {
    gamesError.value = (e as Error).message
  } finally {
    loadingGames.value = false
  }
}

watch(() => props.login, loadProfile, { immediate: true })
</script>

<template>
  <div class="mx-auto w-full max-w-4xl px-4 py-8">
    <div class="space-y-8 rounded-2xl bg-stone-800 p-8">
      <p v-if="notFound" class="text-sm text-stone-500">{{ t('publicProfile.notFound') }}</p>

      <template v-else-if="profile">
        <div>
          <h1 class="text-2xl font-bold">{{ profile.displayName }}</h1>
          <p class="text-sm text-stone-500">@{{ profile.login }}</p>
        </div>

        <p class="whitespace-pre-wrap text-sm text-stone-300">{{ profile.bio || t('profile.bioEmpty') }}</p>

        <div>
          <h2 class="mb-3 text-lg font-semibold">{{ t('publicProfile.games') }}</h2>
          <GameSummaryList :games="games" :has-more="hasMore" :loading="loadingGames" :error="gamesError" @load-more="loadMoreGames" />
        </div>
      </template>
    </div>
  </div>
</template>
