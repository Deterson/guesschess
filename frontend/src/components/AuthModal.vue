<script setup>
import { save as savePendingAction } from '../services/pendingAction'
import { oauthAuthorizationUrl } from '../services/api'

const props = defineProps({
  open: { type: Boolean, required: true },
  pendingAction: { type: Object, required: true },
})
const emit = defineEmits(['anonymous', 'close'])

function continueWithOAuth(provider) {
  savePendingAction(props.pendingAction)
  window.location.href = oauthAuthorizationUrl(provider)
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-10 flex items-center justify-center bg-black/60">
    <div class="w-full max-w-sm space-y-4 rounded-lg bg-stone-800 p-6 shadow-xl">
      <p class="text-center text-stone-200">Comment voulez-vous jouer ?</p>

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
        <button
          type="button"
          class="w-full rounded-lg bg-emerald-600 px-4 py-2 font-semibold hover:bg-emerald-500"
          @click="emit('anonymous')"
        >
          Jouer en anonyme
        </button>
      </div>

      <button type="button" class="w-full text-center text-sm text-stone-400 hover:text-stone-300" @click="emit('close')">
        Annuler
      </button>
    </div>
  </div>
</template>
