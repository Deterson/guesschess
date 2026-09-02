<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { save as savePendingAction } from '../services/pendingAction'
import { oauthAuthorizationUrl } from '../services/api'

const props = defineProps<{
  open: boolean
  returnTo: string
  inGame?: boolean
}>()
const emit = defineEmits<{
  close: []
}>()

const { t } = useI18n()

function continueWithOAuth(provider: string) {
  savePendingAction({ type: 'login', returnTo: props.returnTo })
  window.location.href = oauthAuthorizationUrl(provider)
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-10 flex items-center justify-center bg-black/60">
    <div class="w-full max-w-sm space-y-4 rounded-lg bg-stone-800 p-6 shadow-xl">
      <div class="space-y-1">
        <p class="text-center text-stone-200">{{ t('header.loginModalTitle') }}</p>
        <p v-if="inGame" class="text-center text-xs text-stone-500">{{ t('game.loginModalReminder') }}</p>
      </div>

      <div class="space-y-2">
        <button
          type="button"
          class="w-full rounded-lg bg-stone-700 px-4 py-2 font-semibold hover:bg-stone-600"
          @click="continueWithOAuth('google')"
        >
          {{ t('common.continueWithGoogle') }}
        </button>
        <button
          type="button"
          class="w-full rounded-lg bg-stone-700 px-4 py-2 font-semibold hover:bg-stone-600"
          @click="continueWithOAuth('github')"
        >
          {{ t('common.continueWithGithub') }}
        </button>
      </div>

      <button type="button" class="w-full text-center text-sm text-stone-400 hover:text-stone-300" @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
    </div>
  </div>
</template>
