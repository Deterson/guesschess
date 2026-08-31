<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { ApiError, getMe, updateDisplayName as apiUpdateDisplayName } from '../services/api'

const authStore = useAuthStore()
const { t } = useI18n()
const displayName = ref<string | null>(null)
const editing = ref(false)
const draft = ref('')
const saving = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  const me = await getMe(authStore.token!)
  displayName.value = me.displayName
})

function startEditing() {
  draft.value = displayName.value ?? ''
  error.value = null
  editing.value = true
}

async function save() {
  saving.value = true
  error.value = null
  try {
    const updated = await apiUpdateDisplayName(draft.value, authStore.token!)
    displayName.value = updated.displayName
    editing.value = false
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : t('profile.saveError')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="flex w-full flex-col gap-6 px-4 py-8 sm:flex-row">
    <aside class="sm:w-48 sm:shrink-0">
      <div v-if="!editing" class="mb-6 flex items-center gap-2">
        <h1 class="truncate text-lg font-semibold">{{ displayName ?? '…' }}</h1>
        <button type="button" class="text-xs text-stone-500 hover:text-stone-300" @click="startEditing">{{ t('profile.modify') }}</button>
      </div>
      <div v-else class="mb-6 space-y-2">
        <input
          v-model="draft"
          type="text"
          minlength="3"
          class="w-full rounded bg-stone-800 px-2 py-1 text-sm text-stone-100"
          @keyup.enter="save"
        />
        <p v-if="error" class="text-xs text-red-400">{{ error }}</p>
        <div class="flex gap-2 text-xs">
          <button type="button" class="rounded bg-emerald-600 px-2 py-1 font-semibold hover:bg-emerald-500" :disabled="saving" @click="save">
            {{ t('profile.save') }}
          </button>
          <button type="button" class="text-stone-500 hover:text-stone-300" @click="editing = false">{{ t('common.cancel') }}</button>
        </div>
      </div>

      <nav class="flex flex-col gap-1 text-sm">
        <router-link
          to="/profile/games"
          class="rounded px-2 py-1 hover:bg-stone-800"
          active-class="bg-stone-800 text-white"
        >
          {{ t('profile.myGames') }}
        </router-link>
      </nav>
    </aside>

    <main class="min-w-0 flex-1">
      <router-view />
    </main>
  </div>
</template>
