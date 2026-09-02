<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useAccountStore } from '../stores/account'
import { useGameStore } from '../stores/game'
import { ApiError, completeRegistration, setLogin as apiSetLogin } from '../services/api'
import { consume as consumePendingRegistration, peek as peekPendingRegistration } from '../services/pendingRegistration'
import { resumeAfterLogin } from '../services/postLogin'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const accountStore = useAccountStore()
const gameStore = useGameStore()
const { t } = useI18n()

const login = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)

/**
 * Deux origines possibles pour cet ecran (etape 14) : une inscription fraiche (pas
 * encore de JWT, l'identite est portee par le pendingToken) ou un compte historique
 * deja authentifie qui n'a simplement jamais choisi son login - voir
 * router/index.ts pour la redirection qui ameine ici dans chaque cas.
 */
const isFreshRegistration = computed(() => peekPendingRegistration() !== null)

onMounted(() => {
  if (!isFreshRegistration.value && !authStore.isLoggedIn) {
    router.replace('/')
  }
})

const LOGIN_PATTERN = /^[A-Za-z0-9_][A-Za-z0-9_-]{2,19}$/

const clientError = computed(() => {
  if (!login.value) return null
  return LOGIN_PATTERN.test(login.value) ? null : t('chooseLogin.formatHint')
})

async function submit() {
  if (clientError.value || !login.value || submitting.value) return
  submitting.value = true
  error.value = null
  try {
    if (isFreshRegistration.value) {
      const pendingToken = consumePendingRegistration()!
      const result = await completeRegistration(pendingToken, login.value)
      authStore.login(result.token)
      accountStore.setMe(result.account)
      await resumeAfterLogin(result.token, router, gameStore)
    } else {
      const account = await apiSetLogin(login.value, authStore.token!)
      accountStore.setMe(account)
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
      router.replace(redirect)
    }
  } catch (e) {
    if (e instanceof ApiError && e.code === 'SESSION_EXPIRED') {
      router.replace('/')
      return
    }
    error.value = e instanceof ApiError ? e.message : t('chooseLogin.genericError')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto flex max-w-md flex-col gap-4 px-4 py-16">
    <h1 class="text-xl font-semibold">{{ t('chooseLogin.title') }}</h1>
    <p class="text-sm text-stone-400">{{ t('chooseLogin.explanation') }}</p>

    <form class="flex flex-col gap-2" @submit.prevent="submit">
      <input
        v-model="login"
        type="text"
        autofocus
        autocomplete="off"
        :placeholder="t('chooseLogin.placeholder')"
        class="w-full rounded bg-stone-800 px-3 py-2 text-sm text-stone-100"
      />
      <p v-if="clientError" class="text-xs text-amber-400">{{ clientError }}</p>
      <p v-if="error" class="text-sm text-red-400">{{ error }}</p>
      <button
        type="submit"
        class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold hover:bg-emerald-500 disabled:opacity-50"
        :disabled="submitting || !login || Boolean(clientError)"
      >
        {{ submitting ? t('common.connecting') : t('chooseLogin.confirm') }}
      </button>
    </form>
  </div>
</template>
