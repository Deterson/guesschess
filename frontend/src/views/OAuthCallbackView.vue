<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useGameStore } from '../stores/game'
import { resumeAfterLogin } from '../services/postLogin'
import { save as savePendingRegistration } from '../services/pendingRegistration'

const router = useRouter()
const authStore = useAuthStore()
const gameStore = useGameStore()
const { t } = useI18n()
const error = ref<string | null>(null)

onMounted(async () => {
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
  const pendingToken = params.get('pendingToken')
  const token = params.get('token')

  // Etape 14 : identite OAuth verifiee mais aucun compte ne lui correspond encore -
  // le pendingAction (creer/rejoindre une partie) reste en sessionStorage, il sera
  // rejoue par ChooseLoginView une fois l'inscription terminee.
  if (pendingToken) {
    savePendingRegistration(pendingToken)
    history.replaceState(null, '', window.location.pathname)
    router.replace('/choose-login')
    return
  }

  if (!token) {
    router.replace('/')
    return
  }
  authStore.login(token)
  history.replaceState(null, '', window.location.pathname)

  try {
    await resumeAfterLogin(token, router, gameStore)
  } catch (e) {
    error.value = (e as Error).message
  }
})
</script>

<template>
  <div class="mx-auto flex max-w-xl flex-col items-center gap-4 px-4 py-16 text-center">
    <p v-if="!error" class="text-stone-300">{{ t('oauthCallback.connecting') }}</p>
    <template v-else>
      <p class="text-red-400">{{ error }}</p>
      <router-link to="/" class="rounded-lg bg-stone-700 px-4 py-2 hover:bg-stone-600">{{ t('common.backToHome') }}</router-link>
    </template>
  </div>
</template>
