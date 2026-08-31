<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { save as savePendingAction } from '../services/pendingAction'
import { oauthAuthorizationUrl } from '../services/api'

const authStore = useAuthStore()
const route = useRoute()
const showLoginModal = ref(false)

function continueWithOAuth(provider: string) {
  savePendingAction({ type: 'login', returnTo: route.fullPath })
  window.location.href = oauthAuthorizationUrl(provider)
}
</script>

<template>
  <header class="flex items-center justify-between gap-4 border-b border-stone-800 px-4 py-3">
    <nav class="flex items-center gap-4 text-sm font-semibold text-stone-300">
      <router-link to="/" class="hover:text-white">Jouer</router-link>
      <router-link to="/how-to-play" class="hover:text-white">Tutoriel</router-link>
    </nav>

    <router-link v-if="authStore.isLoggedIn" to="/profile" class="text-sm font-semibold text-stone-300 hover:text-white">
      Profil
    </router-link>
    <button v-else type="button" class="text-sm font-semibold text-stone-300 hover:text-white" @click="showLoginModal = true">
      Connexion
    </button>
  </header>

  <div v-if="showLoginModal" class="fixed inset-0 z-10 flex items-center justify-center bg-black/60">
    <div class="w-full max-w-sm space-y-4 rounded-lg bg-stone-800 p-6 shadow-xl">
      <p class="text-center text-stone-200">Se connecter</p>

      <div class="space-y-2">
        <button
          type="button"
          class="w-full rounded-lg bg-stone-700 px-4 py-2 font-semibold hover:bg-stone-600"
          @click="continueWithOAuth('google')"
        >
          Continuer avec Google
        </button>
        <button
          type="button"
          class="w-full rounded-lg bg-stone-700 px-4 py-2 font-semibold hover:bg-stone-600"
          @click="continueWithOAuth('github')"
        >
          Continuer avec GitHub
        </button>
      </div>

      <button type="button" class="w-full text-center text-sm text-stone-400 hover:text-stone-300" @click="showLoginModal = false">
        Annuler
      </button>
    </div>
  </div>
</template>
