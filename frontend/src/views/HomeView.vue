<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createGame } from '../services/api'
import { useGameStore } from '../stores/game'
import { useAuthStore } from '../stores/auth'
import AuthModal from '../components/AuthModal.vue'
import CreateGameModal from '../components/CreateGameModal.vue'
import LoginModal from '../components/LoginModal.vue'
import type { Color } from '../types/api'

const router = useRouter()
const gameStore = useGameStore()
const authStore = useAuthStore()
const { t } = useI18n()
const creating = ref(false)
const showCreateModal = ref(false)
const showAuthModal = ref(false)
const showLoginModal = ref(false)
const error = ref<string | null>(null)
const noGuessmate = ref(false)
const color = ref<Color | 'RANDOM'>('RANDOM')

function openCreateModal() {
  error.value = null
  showCreateModal.value = true
}

function onCreateModalConfirm(choice: { color: Color | 'RANDOM'; noGuessmate: boolean }) {
  showCreateModal.value = false
  color.value = choice.color
  noGuessmate.value = choice.noGuessmate
  if (authStore.isLoggedIn) {
    create(authStore.token)
  } else {
    showAuthModal.value = true
  }
}

async function create(authToken: string | null) {
  showAuthModal.value = false
  creating.value = true
  error.value = null
  try {
    const variant = noGuessmate.value ? 'NOGUESSMATE' : 'GUESSCHESS'
    const created = await createGame(variant, color.value, authToken)
    // Le token/couleur revenus ici sont déjà vérifiés côté serveur - on peuple
    // directement le store plutôt que de forcer GameView à les redécouvrir via
    // /my-access, dont la fiabilité dépend de la propagation immédiate du cookie
    // anonyme qu'on vient tout juste de poser (pas garanti au tout premier appel).
    await gameStore.joinGame({
      gameId: created.gameId,
      token: created.creatorToken,
      color: created.creatorColor.toLowerCase() as 'white' | 'black',
    })
    router.push(`/game/${created.gameId}`)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    creating.value = false
  }
}

function continueAnonymously() {
  create(null)
}

function openMyGames() {
  if (authStore.isLoggedIn) {
    router.push('/profile')
  } else {
    showLoginModal.value = true
  }
}
</script>

<template>
  <div class="mx-auto flex max-w-xl flex-col items-center gap-6 px-4 py-16 text-center">
    <h1 class="text-3xl font-bold">Guesschess</h1>
    <p class="text-stone-400">
      {{ t('home.tagline') }}
    </p>

    <p v-if="error" class="text-sm text-red-400">{{ error }}</p>

    <router-link to="/how-to-play" class="font-semibold text-emerald-400 underline hover:text-emerald-300">
      {{ t('header.tutorial') }}
    </router-link>

    <div class="flex flex-col items-center gap-3">
      <button
        type="button"
        class="rounded-lg bg-emerald-600 px-6 py-3 font-semibold hover:bg-emerald-500 disabled:opacity-50"
        :disabled="creating"
        @click="openCreateModal"
      >
        {{ creating ? t('home.creating') : t('home.createButton') }}
      </button>

      <button type="button" class="rounded-lg bg-stone-700 px-6 py-2 font-semibold hover:bg-stone-600" @click="openMyGames">
        {{ t('profile.myGames') }}
      </button>
    </div>

    <CreateGameModal :open="showCreateModal" @confirm="onCreateModalConfirm" @close="showCreateModal = false" />

    <AuthModal
      :open="showAuthModal"
      :pending-action="{ type: 'create', variant: noGuessmate ? 'NOGUESSMATE' : 'GUESSCHESS', color }"
      @anonymous="continueAnonymously"
      @close="showAuthModal = false"
    />

    <LoginModal :open="showLoginModal" return-to="/profile" @close="showLoginModal = false" />
  </div>
</template>
