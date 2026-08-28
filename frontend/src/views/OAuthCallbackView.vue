<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useGameStore } from '../stores/game'
import { consume as consumePendingAction } from '../services/pendingAction'
import { mark as markJustCreated } from '../services/justCreated'
import { createGame, joinGame } from '../services/api'

const router = useRouter()
const authStore = useAuthStore()
const gameStore = useGameStore()
const error = ref<string | null>(null)

onMounted(async () => {
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
  const token = params.get('token')
  if (!token) {
    router.replace('/')
    return
  }
  authStore.login(token)
  history.replaceState(null, '', window.location.pathname)

  const action = consumePendingAction()
  if (!action) {
    router.replace('/')
    return
  }

  try {
    if (action.type === 'create') {
      const created = await createGame(action.variant, action.color, token)
      markJustCreated(created.gameId)
      await gameStore.joinGame({
        gameId: created.gameId,
        token: created.creatorToken,
        color: created.creatorColor.toLowerCase() as 'white' | 'black',
      })
      router.replace(`/game/${created.gameId}`)
    } else if (action.type === 'join') {
      const joined = await joinGame(action.gameId, token)
      await gameStore.joinGame({ gameId: joined.gameId, token: joined.token, color: joined.color.toLowerCase() as 'white' | 'black' })
      router.replace(`/game/${action.gameId}`)
    } else {
      router.replace('/')
    }
  } catch (e) {
    error.value = (e as Error).message
  }
})
</script>

<template>
  <div class="mx-auto flex max-w-xl flex-col items-center gap-4 px-4 py-16 text-center">
    <p v-if="!error" class="text-stone-300">Connexion en cours…</p>
    <template v-else>
      <p class="text-red-400">{{ error }}</p>
      <router-link to="/" class="rounded-lg bg-stone-700 px-4 py-2 hover:bg-stone-600">Retour à l'accueil</router-link>
    </template>
  </div>
</template>
