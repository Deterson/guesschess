<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { ApiError, adminListGames, adminListUsers } from '../services/api'
import type { AdminGameHttpResponse, AdminUserHttpResponse } from '../types/api'

/**
 * Etape 18 : page admin en lecture seule, sans point d'entree dans l'UI (accessible en
 * tapant /admin - le backend rejette en 403 si le compte connecte n'est pas dans
 * ADMIN_EMAILS, voir AdminAccessService). Remplace la lecture directe en base par SSH
 * documentee dans .github/CLAUDE.md.
 */

const PAGE_SIZE = 30

const { t } = useI18n()
const authStore = useAuthStore()

const forbidden = ref(false)
const loadError = ref<string | null>(null)

const users = ref<AdminUserHttpResponse[]>([])
const usersPage = ref(0)
const usersHasMore = ref(true)
const usersLoading = ref(false)
const search = ref('')

const games = ref<AdminGameHttpResponse[]>([])
const gamesPage = ref(0)
const gamesHasMore = ref(true)
const gamesLoading = ref(false)

function handleError(e: unknown) {
  if (e instanceof ApiError && e.status === 403) {
    forbidden.value = true
    return
  }
  loadError.value = e instanceof Error ? e.message : String(e)
}

async function loadMoreUsers() {
  if (forbidden.value) return
  usersLoading.value = true
  try {
    const next = await adminListUsers(usersPage.value, PAGE_SIZE, search.value, authStore.token!)
    users.value.push(...next)
    usersHasMore.value = next.length === PAGE_SIZE
    usersPage.value += 1
  } catch (e) {
    handleError(e)
  } finally {
    usersLoading.value = false
  }
}

async function loadMoreGames() {
  if (forbidden.value) return
  gamesLoading.value = true
  try {
    const next = await adminListGames(gamesPage.value, PAGE_SIZE, authStore.token!)
    games.value.push(...next)
    gamesHasMore.value = next.length === PAGE_SIZE
    gamesPage.value += 1
  } catch (e) {
    handleError(e)
  } finally {
    gamesLoading.value = false
  }
}

let searchTimeout: ReturnType<typeof setTimeout> | undefined
watch(search, () => {
  clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    users.value = []
    usersPage.value = 0
    usersHasMore.value = true
    loadMoreUsers()
  }, 300)
})

function playerLabel(label: string | null, type: string | null): string {
  if (type === null) return '—'
  if (type === 'ANONYMOUS') return t('admin.playerAnonymous')
  if (type === 'COMPUTER') return t('admin.playerComputer', { level: label })
  return label ? `@${label}` : '—'
}

onMounted(() => {
  loadMoreUsers()
  loadMoreGames()
})
</script>

<template>
  <div class="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-10 px-4 py-8 text-sm text-stone-300">
    <h1 class="text-xl font-semibold text-stone-100">{{ t('admin.title') }}</h1>

    <p v-if="forbidden" class="text-red-400">{{ t('admin.forbidden') }}</p>
    <p v-else-if="loadError" class="text-red-400">{{ loadError }}</p>

    <template v-else>
      <section>
        <h2 class="mb-3 text-lg font-semibold text-stone-100">{{ t('admin.users') }} ({{ users.length }})</h2>
        <input
          v-model="search"
          type="text"
          :placeholder="t('admin.searchPlaceholder')"
          class="mb-3 w-full max-w-sm rounded border border-stone-700 bg-stone-900 px-3 py-1.5 text-stone-200 outline-none focus:border-stone-500"
        />
        <div class="overflow-x-auto rounded border border-stone-800">
          <table class="w-full min-w-[600px] text-left">
            <thead class="bg-stone-900 text-stone-400">
              <tr>
                <th class="px-3 py-2">{{ t('admin.table.login') }}</th>
                <th class="px-3 py-2">{{ t('admin.table.displayName') }}</th>
                <th class="px-3 py-2">{{ t('admin.table.email') }}</th>
                <th class="px-3 py-2">{{ t('admin.table.createdAt') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in users" :key="u.id" class="border-t border-stone-800">
                <td class="px-3 py-1.5">{{ u.login ? `@${u.login}` : '—' }}</td>
                <td class="px-3 py-1.5">{{ u.displayName }}</td>
                <td class="px-3 py-1.5">{{ u.email ?? '—' }}</td>
                <td class="px-3 py-1.5">{{ new Date(u.createdAt).toLocaleString() }}</td>
              </tr>
              <tr v-if="!usersLoading && users.length === 0">
                <td colspan="4" class="px-3 py-3 text-center text-stone-500">{{ t('admin.noResults') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          v-if="usersHasMore"
          class="mt-3 rounded border border-stone-700 px-3 py-1.5 hover:bg-stone-800 disabled:opacity-50"
          :disabled="usersLoading"
          @click="loadMoreUsers"
        >{{ t('admin.loadMore') }}</button>
      </section>

      <section>
        <h2 class="mb-3 text-lg font-semibold text-stone-100">{{ t('admin.games') }} ({{ games.length }})</h2>
        <div class="overflow-x-auto rounded border border-stone-800">
          <table class="w-full min-w-[900px] text-left">
            <thead class="bg-stone-900 text-stone-400">
              <tr>
                <th class="px-3 py-2">{{ t('admin.table.id') }}</th>
                <th class="px-3 py-2">{{ t('admin.table.variant') }}</th>
                <th class="px-3 py-2">{{ t('admin.table.status') }}</th>
                <th class="px-3 py-2">{{ t('admin.table.result') }}</th>
                <th class="px-3 py-2">{{ t('admin.table.white') }}</th>
                <th class="px-3 py-2">{{ t('admin.table.black') }}</th>
                <th class="px-3 py-2">{{ t('admin.table.updatedAt') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="g in games" :key="g.id" class="border-t border-stone-800">
                <td class="px-3 py-1.5 font-mono text-xs text-stone-500">{{ g.id.slice(0, 8) }}</td>
                <td class="px-3 py-1.5">{{ g.variant }}</td>
                <td class="px-3 py-1.5">{{ g.status }}</td>
                <td class="px-3 py-1.5">{{ g.resultCause ?? '—' }}</td>
                <td class="px-3 py-1.5">{{ playerLabel(g.whiteLabel, g.whiteType) }}</td>
                <td class="px-3 py-1.5">{{ playerLabel(g.blackLabel, g.blackType) }}</td>
                <td class="px-3 py-1.5">{{ new Date(g.updatedAt).toLocaleString() }}</td>
              </tr>
              <tr v-if="!gamesLoading && games.length === 0">
                <td colspan="7" class="px-3 py-3 text-center text-stone-500">{{ t('admin.noResults') }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <button
          v-if="gamesHasMore"
          class="mt-3 rounded border border-stone-700 px-3 py-1.5 hover:bg-stone-800 disabled:opacity-50"
          :disabled="gamesLoading"
          @click="loadMoreGames"
        >{{ t('admin.loadMore') }}</button>
      </section>
    </template>
  </div>
</template>
