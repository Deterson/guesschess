<script setup>
import { ref } from 'vue'
import { useGameStore } from '../stores/game'

const gameStore = useGameStore()
const creating = ref(false)
const links = ref(null)

async function createGame() {
  creating.value = true
  try {
    const created = await gameStore.createGame()
    const path = `/game/${created.gameId}`
    const base = `${window.location.origin}${path}`
    links.value = {
      white: { url: `${base}?token=${created.whiteToken}&color=white`, path: `${path}?token=${created.whiteToken}&color=white` },
      black: { url: `${base}?token=${created.blackToken}&color=black`, path: `${path}?token=${created.blackToken}&color=black` },
    }
  } finally {
    creating.value = false
  }
}

async function copy(text) {
  await navigator.clipboard.writeText(text)
}
</script>

<template>
  <div class="mx-auto flex max-w-xl flex-col items-center gap-6 px-4 py-16 text-center">
    <h1 class="text-3xl font-bold">Guesschess</h1>
    <p class="text-stone-400">
      Échecs classiques, avec une règle en plus : à chaque coup, votre adversaire essaie de deviner ce que vous allez
      jouer.
    </p>

    <button
      type="button"
      class="rounded-lg bg-emerald-600 px-6 py-3 font-semibold hover:bg-emerald-500 disabled:opacity-50"
      :disabled="creating"
      @click="createGame"
    >
      {{ creating ? 'Création…' : 'Créer une partie' }}
    </button>

    <div v-if="links" class="w-full space-y-4 text-left">
      <p class="text-stone-300">Partagez un des deux liens avec votre adversaire, gardez l'autre pour vous :</p>

      <div class="space-y-2 rounded-lg bg-stone-800 p-4">
        <p class="text-sm font-semibold">Lien Blancs</p>
        <div class="flex gap-2">
          <input readonly class="flex-1 truncate rounded bg-stone-900 px-2 py-1 text-xs" :value="links.white.url" />
          <button type="button" class="rounded bg-stone-700 px-3 py-1 text-sm hover:bg-stone-600" @click="copy(links.white.url)">
            Copier
          </button>
          <router-link :to="links.white.path" class="rounded bg-emerald-700 px-3 py-1 text-sm hover:bg-emerald-600">
            Jouer
          </router-link>
        </div>
      </div>

      <div class="space-y-2 rounded-lg bg-stone-800 p-4">
        <p class="text-sm font-semibold">Lien Noirs</p>
        <div class="flex gap-2">
          <input readonly class="flex-1 truncate rounded bg-stone-900 px-2 py-1 text-xs" :value="links.black.url" />
          <button type="button" class="rounded bg-stone-700 px-3 py-1 text-sm hover:bg-stone-600" @click="copy(links.black.url)">
            Copier
          </button>
          <router-link :to="links.black.path" class="rounded bg-emerald-700 px-3 py-1 text-sm hover:bg-emerald-600">
            Jouer
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
