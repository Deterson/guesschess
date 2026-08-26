<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createGame } from '../services/api'
import AuthModal from '../components/AuthModal.vue'

const router = useRouter()
const creating = ref(false)
const showModal = ref(false)
const error = ref(null)
const guessmate = ref(false)
const color = ref('RANDOM')

function openModal() {
  error.value = null
  showModal.value = true
}

async function create(authToken) {
  showModal.value = false
  creating.value = true
  error.value = null
  try {
    const variant = guessmate.value ? 'GUESSMATE' : 'GUESSCHESS'
    const created = await createGame(variant, color.value, authToken)
    router.push({
      path: `/game/${created.gameId}`,
      query: {
        token: created.creatorToken,
        color: created.creatorColor.toLowerCase(),
        inviteToken: created.opponentToken,
        inviteColor: created.opponentColor.toLowerCase(),
      },
    })
  } catch (e) {
    error.value = e.message
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
      Échecs classiques, avec une règle en plus : à chaque coup, votre adversaire essaie de deviner ce que vous allez
      jouer.
    </p>

    <fieldset class="w-full rounded-lg bg-stone-800 px-4 py-3 text-left">
      <legend class="px-1 text-sm font-semibold">Votre couleur</legend>
      <div class="flex gap-4 text-sm text-stone-300">
        <label class="flex items-center gap-2">
          <input type="radio" v-model="color" value="WHITE" class="accent-emerald-600" />
          Blancs
        </label>
        <label class="flex items-center gap-2">
          <input type="radio" v-model="color" value="BLACK" class="accent-emerald-600" />
          Noirs
        </label>
        <label class="flex items-center gap-2">
          <input type="radio" v-model="color" value="RANDOM" class="accent-emerald-600" />
          Aléatoire
        </label>
      </div>
    </fieldset>

    <label class="flex items-center gap-3 rounded-lg bg-stone-800 px-4 py-3 text-sm">
      <input type="checkbox" v-model="guessmate" class="h-4 w-4 accent-emerald-600" />
      <span class="text-left">
        <span class="font-semibold">Variante Guessmate</span>
        <br />
        <span class="text-stone-400">
          Deviner correctement le coup qui pare un échec met fin à la partie immédiatement, au lieu de simplement
          annuler le coup.
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
      {{ creating ? 'Création…' : `Créer une partie${guessmate ? ' (Guessmate)' : ''}` }}
    </button>

    <AuthModal
      :open="showModal"
      :pending-action="{ type: 'create', variant: guessmate ? 'GUESSMATE' : 'GUESSCHESS', color }"
      @anonymous="continueAnonymously"
      @close="showModal = false"
    />
  </div>
</template>
