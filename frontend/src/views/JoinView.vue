<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { joinGame } from '../services/api'
import AuthModal from '../components/AuthModal.vue'

const props = defineProps({
  gameId: { type: String, required: true },
  token: { type: String, required: true },
  color: { type: String, required: true },
})

const router = useRouter()
const authStore = useAuthStore()
const showModal = ref(false)
const error = ref(null)

async function accept(authToken) {
  error.value = null
  try {
    const joined = await joinGame(props.gameId, props.token, authToken)
    router.replace({ path: `/game/${joined.gameId}`, query: { token: joined.token, color: joined.color.toLowerCase() } })
  } catch (e) {
    error.value = e.status === 409 ? 'Cette invitation a déjà été utilisée.' : e.message
    showModal.value = false
  }
}

function continueAnonymously() {
  accept(null)
}

onMounted(() => {
  if (authStore.isLoggedIn) {
    accept(authStore.token)
  } else {
    showModal.value = true
  }
})
</script>

<template>
  <div class="mx-auto flex max-w-xl flex-col items-center gap-4 px-4 py-16 text-center">
    <p v-if="error" class="text-red-400">{{ error }}</p>
    <p v-else-if="!showModal" class="text-stone-300">Connexion à la partie…</p>

    <AuthModal
      :open="showModal"
      :pending-action="{ type: 'join', gameId, token, color }"
      @anonymous="continueAnonymously"
      @close="showModal = false"
    />
  </div>
</template>
