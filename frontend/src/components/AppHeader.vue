<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { save as savePendingAction } from '../services/pendingAction'
import { oauthAuthorizationUrl } from '../services/api'
import LanguageSwitch from './LanguageSwitch.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const showLoginModal = ref(false)

function continueWithOAuth(provider: string) {
  savePendingAction({ type: 'login', returnTo: route.fullPath })
  window.location.href = oauthAuthorizationUrl(provider)
}

function logout() {
  authStore.logout()
  router.push('/')
}
</script>

<template>
  <header class="flex items-center justify-between gap-4 border-b border-stone-800 px-4 py-3">
    <nav class="flex items-center gap-4 text-sm font-semibold text-stone-300">
      <router-link to="/" class="hover:text-white">{{ t('header.play') }}</router-link>
      <router-link to="/how-to-play" class="hover:text-white">{{ t('header.tutorial') }}</router-link>
    </nav>

    <div class="flex items-center gap-4">
      <template v-if="authStore.isLoggedIn">
        <router-link to="/profile" class="text-sm font-semibold text-stone-300 hover:text-white">
          {{ t('header.profile') }}
        </router-link>
        <button type="button" class="cursor-pointer text-sm font-semibold text-stone-300 hover:text-white" @click="logout">
          {{ t('header.logout') }}
        </button>
      </template>
      <button v-else type="button" class="cursor-pointer text-sm font-semibold text-stone-300 hover:text-white" @click="showLoginModal = true">
        {{ t('header.login') }}
      </button>

      <LanguageSwitch />
    </div>
  </header>

  <div v-if="showLoginModal" class="fixed inset-0 z-10 flex items-center justify-center bg-black/60">
    <div class="w-full max-w-sm space-y-4 rounded-lg bg-stone-800 p-6 shadow-xl">
      <p class="text-center text-stone-200">{{ t('header.loginModalTitle') }}</p>

      <div class="space-y-2">
        <button
          type="button"
          class="w-full rounded-lg bg-stone-700 px-4 py-2 font-semibold hover:bg-stone-600"
          @click="continueWithOAuth('google')"
        >
          {{ t('common.continueWithGoogle') }}
        </button>
        <button
          type="button"
          class="w-full rounded-lg bg-stone-700 px-4 py-2 font-semibold hover:bg-stone-600"
          @click="continueWithOAuth('github')"
        >
          {{ t('common.continueWithGithub') }}
        </button>
      </div>

      <button type="button" class="w-full text-center text-sm text-stone-400 hover:text-stone-300" @click="showLoginModal = false">
        {{ t('common.cancel') }}
      </button>
    </div>
  </div>
</template>
