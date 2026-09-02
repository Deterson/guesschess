<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useAccountStore } from '../stores/account'
import { ApiError, updateBio as apiUpdateBio } from '../services/api'
import EditDisplayNameModal from '../components/EditDisplayNameModal.vue'
import EditIcon from '../components/EditIcon.vue'

const authStore = useAuthStore()
const accountStore = useAccountStore()
const { t } = useI18n()

const showEditName = ref(false)

const editingBio = ref(false)
const bioDraft = ref('')
const savingBio = ref(false)
const bioError = ref<string | null>(null)

function startEditingBio() {
  bioDraft.value = accountStore.me?.bio ?? ''
  bioError.value = null
  editingBio.value = true
}

async function saveBio() {
  savingBio.value = true
  bioError.value = null
  try {
    const account = await apiUpdateBio(bioDraft.value, authStore.token!)
    accountStore.setMe(account)
    editingBio.value = false
  } catch (e) {
    bioError.value = e instanceof ApiError ? e.message : t('profile.saveError')
  } finally {
    savingBio.value = false
  }
}
</script>

<template>
  <div class="max-w-xl space-y-8">
    <div>
      <button type="button" class="group flex items-center gap-2" @click="showEditName = true">
        <span class="text-2xl font-bold text-stone-100 group-hover:text-emerald-400">{{ accountStore.me?.displayName }}</span>
        <EditIcon class="h-5 w-5 shrink-0 text-stone-500 group-hover:text-emerald-400" />
      </button>
      <p class="text-sm text-stone-500">@{{ accountStore.me?.login }}</p>
    </div>

    <section class="space-y-2">
      <div v-if="!editingBio" class="space-y-2">
        <p class="whitespace-pre-wrap text-sm text-stone-300">{{ accountStore.me?.bio || t('profile.bioEmpty') }}</p>
        <button
          type="button"
          class="text-stone-500 hover:text-emerald-400"
          :aria-label="t('profile.modify')"
          :title="t('profile.modify')"
          @click="startEditingBio"
        >
          <EditIcon class="h-5 w-5" />
        </button>
      </div>
      <div v-else class="space-y-2">
        <textarea
          v-model="bioDraft"
          rows="6"
          maxlength="5000"
          class="w-full rounded bg-stone-800 px-2 py-1 text-sm text-stone-100"
        ></textarea>
        <p class="text-right text-xs text-stone-500">{{ bioDraft.length }}/5000</p>
        <p v-if="bioError" class="text-xs text-red-400">{{ bioError }}</p>
        <div class="flex gap-2">
          <button
            type="button"
            class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold hover:bg-emerald-500 disabled:opacity-50"
            :disabled="savingBio"
            @click="saveBio"
          >
            {{ t('profile.save') }}
          </button>
          <button type="button" class="rounded-lg bg-stone-700 px-4 py-2 text-sm font-semibold hover:bg-stone-600" @click="editingBio = false">
            {{ t('common.cancel') }}
          </button>
        </div>
      </div>
    </section>

    <EditDisplayNameModal :open="showEditName" @close="showEditName = false" />
  </div>
</template>
