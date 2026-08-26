<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { consume as consumePendingAction } from '../services/pendingAction'
import { createGame, joinGame } from '../services/api'

const router = useRouter()
const authStore = useAuthStore()
const error = ref(null)

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
      router.replace({
        path: `/game/${created.gameId}`,
        query: {
          token: created.creatorToken,
          color: created.creatorColor.toLowerCase(),
          inviteToken: created.opponentToken,
          inviteColor: created.opponentColor.toLowerCase(),
        },
      })
    } else if (action.type === 'join') {
      const joined = await joinGame(action.gameId, action.token, token)
      router.replace({ path: `/game/${joined.gameId}`, query: { token: joined.token, color: joined.color.toLowerCase() } })
    } else {
      router.replace('/')
    }
  } catch (e) {
    error.value = e.message
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
