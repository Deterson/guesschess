<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useAccountStore } from '../stores/account'
import { ApiError, updateDisplayName as apiUpdateDisplayName } from '../services/api'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const authStore = useAuthStore()
const accountStore = useAccountStore()
const { t } = useI18n()

const nameDraft = ref('')
const saving = ref(false)
const error = ref<string | null>(null)

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) return
    nameDraft.value = accountStore.me?.displayName ?? ''
    error.value = null
  },
)

async function save() {
  saving.value = true
  error.value = null
  try {
    const account = await apiUpdateDisplayName(nameDraft.value, authStore.token!)
    accountStore.setMe(account)
    emit('close')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : t('profile.saveError')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-10 flex items-center justify-center bg-black/60">
    <div class="w-full max-w-sm space-y-4 rounded-lg bg-stone-800 p-6 shadow-xl">
      <p class="text-center text-stone-200">{{ t('profile.editDisplayNameTitle') }}</p>

      <div class="space-y-2">
        <input
          v-model="nameDraft"
          type="text"
          minlength="2"
          maxlength="32"
          autofocus
          class="w-full rounded bg-stone-900 px-3 py-2 text-sm text-stone-100"
          @keyup.enter="save"
        />
        <p v-if="error" class="text-xs text-red-400">{{ error }}</p>
      </div>

      <div class="flex gap-2">
        <button
          type="button"
          class="flex-1 rounded-lg bg-emerald-600 px-4 py-2 font-semibold hover:bg-emerald-500 disabled:opacity-50"
          :disabled="saving"
          @click="save"
        >
          {{ t('profile.save') }}
        </button>
        <button type="button" class="flex-1 rounded-lg bg-stone-700 px-4 py-2 font-semibold hover:bg-stone-600" @click="emit('close')">
          {{ t('common.cancel') }}
        </button>
      </div>
    </div>
  </div>
</template>
