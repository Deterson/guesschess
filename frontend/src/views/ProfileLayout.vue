<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAccountStore } from '../stores/account'

const accountStore = useAccountStore()
const { t } = useI18n()
</script>

<template>
  <div class="flex w-full flex-col gap-6 px-4 py-8 sm:flex-row">
    <aside class="sm:w-48 sm:shrink-0">
      <h1 class="mb-6 truncate text-lg font-semibold">{{ accountStore.me?.displayName ?? '…' }}</h1>

      <nav class="flex flex-col gap-1 text-sm">
        <router-link
          to="/my-profile/about"
          class="rounded px-2 py-1 hover:bg-stone-800"
          active-class="bg-stone-800 text-white"
        >
          {{ t('profile.myProfile') }}
        </router-link>
        <router-link
          to="/my-profile/games"
          class="rounded px-2 py-1 hover:bg-stone-800"
          active-class="bg-stone-800 text-white"
        >
          {{ t('profile.myGames') }}
        </router-link>
        <router-link
          to="/my-profile/settings"
          class="rounded px-2 py-1 hover:bg-stone-800"
          active-class="bg-stone-800 text-white"
        >
          {{ t('profile.settings') }}
        </router-link>
      </nav>
    </aside>

    <main class="min-w-0 flex-1">
      <router-view />
    </main>
  </div>
</template>
