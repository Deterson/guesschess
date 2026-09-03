<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useSettingsStore } from '../stores/settings'
import { useThemeStore } from '../stores/theme'
import { ApiError } from '../services/api'

const authStore = useAuthStore()
const settingsStore = useSettingsStore()
const themeStore = useThemeStore()
const router = useRouter()
const { t } = useI18n()
const error = ref<string | null>(null)

onMounted(() => {
  if (!settingsStore.loaded) {
    settingsStore.load(authStore.token!).catch((e) => {
      if (e instanceof ApiError && e.code === 'SESSION_EXPIRED') router.replace('/')
    })
  }
})

async function onToggleTurnBlinkReminder(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  error.value = null
  try {
    await settingsStore.setTurnBlinkReminder(checked, authStore.token!)
  } catch (e) {
    error.value = (e as Error).message || t('profileSettings.saveError')
  }
}
</script>

<template>
  <div class="space-y-3 lg:w-1/2">
    <h2 class="text-lg font-semibold">{{ t('profile.settings') }}</h2>

    <p v-if="error" class="text-sm text-red-400">{{ error }}</p>

    <label class="flex items-center gap-2 text-sm">
      <input
        type="checkbox"
        :checked="settingsStore.turnBlinkReminder"
        class="h-4 w-4 rounded"
        @change="onToggleTurnBlinkReminder"
      />
      {{ t('profileSettings.turnBlinkReminder') }}
    </label>

    <label class="flex items-center gap-2 text-sm">
      <input
        type="checkbox"
        :checked="themeStore.nightMode"
        class="h-4 w-4 rounded"
        @change="themeStore.setNightMode(($event.target as HTMLInputElement).checked)"
      />
      {{ t('profileSettings.nightMode') }}
    </label>
  </div>
</template>
