<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { createGame } from '../services/api'
import { useGameStore } from '../stores/game'
import { useAuthStore } from '../stores/auth'
import AuthModal from '../components/AuthModal.vue'
import type { Color } from '../types/api'

const router = useRouter()
const gameStore = useGameStore()
const authStore = useAuthStore()
const { t } = useI18n()
const creating = ref(false)
const showModal = ref(false)
const error = ref<string | null>(null)
const guessmate = ref(false)
const color = ref<Color | 'RANDOM'>('RANDOM')

function openModal() {
  error.value = null
  if (authStore.isLoggedIn) {
    create(authStore.token)
  } else {
    showModal.value = true
  }
}

async function create(authToken: string | null) {
  showModal.value = false
  creating.value = true
  error.value = null
  try {
    const variant = guessmate.value ? 'GUESSMATE' : 'GUESSCHESS'
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
</script>

<template>
  <div class="mx-auto flex max-w-xl flex-col items-center gap-6 px-4 py-16 text-center">
    <h1 class="text-3xl font-bold">Guesschess</h1>
    <p class="text-stone-400">
      {{ t('home.tagline') }}
    </p>

    <fieldset class="w-full rounded-lg bg-stone-800 px-4 py-3 text-left">
      <legend class="px-1 text-sm font-semibold">{{ t('home.colorLegend') }}</legend>
      <div class="flex gap-4 text-sm text-stone-300">
        <label class="flex items-center gap-2">
          <input type="radio" v-model="color" value="WHITE" class="accent-emerald-600" />
          {{ t('common.white') }}
        </label>
        <label class="flex items-center gap-2">
          <input type="radio" v-model="color" value="BLACK" class="accent-emerald-600" />
          {{ t('common.black') }}
        </label>
        <label class="flex items-center gap-2">
          <input type="radio" v-model="color" value="RANDOM" class="accent-emerald-600" />
          {{ t('home.random') }}
        </label>
      </div>
    </fieldset>

    <label class="flex items-center gap-3 rounded-lg bg-stone-800 px-4 py-3 text-sm">
      <input type="checkbox" v-model="guessmate" class="h-4 w-4 accent-emerald-600" />
      <span class="text-left">
        <span class="font-semibold">{{ t('home.guessmateTitle') }}</span>
        <br />
        <span class="text-stone-400">
          {{ t('home.guessmateDescription') }}
        </span>
      </span>
    </label>

    <p v-if="error" class="text-sm text-red-400">{{ error }}</p>

    <button
      type="button"
      class="rounded-lg bg-emerald-600 px-6 py-3 font-semibold hover:bg-emerald-500 disabled:opacity-50"
      :disabled="creating"
      @click="openModal"
    >
      {{ creating ? t('home.creating') : `${t('home.createButton')}${guessmate ? t('home.guessmateSuffix') : ''}` }}
    </button>

    <AuthModal
      :open="showModal"
      :pending-action="{ type: 'create', variant: guessmate ? 'GUESSMATE' : 'GUESSCHESS', color }"
      @anonymous="continueAnonymously"
      @close="showModal = false"
    />
  </div>
</template>
