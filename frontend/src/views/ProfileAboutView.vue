<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { useAccountStore } from '../stores/account'
import { ApiError, updateBio as apiUpdateBio, updateDisplayName as apiUpdateDisplayName } from '../services/api'

const authStore = useAuthStore()
const accountStore = useAccountStore()
const { t } = useI18n()

const editingName = ref(false)
const nameDraft = ref('')
const savingName = ref(false)
const nameError = ref<string | null>(null)

function startEditingName() {
  nameDraft.value = accountStore.me?.displayName ?? ''
  nameError.value = null
  editingName.value = true
}

async function saveName() {
  savingName.value = true
  nameError.value = null
  try {
    const account = await apiUpdateDisplayName(nameDraft.value, authStore.token!)
    accountStore.setMe(account)
    editingName.value = false
  } catch (e) {
    nameError.value = e instanceof ApiError ? e.message : t('profile.saveError')
  } finally {
    savingName.value = false
  }
}

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
    <h2 class="text-lg font-semibold">{{ t('profile.myProfile') }}</h2>

    <section class="space-y-1">
      <p class="text-xs uppercase tracking-wide text-stone-500">{{ t('profile.login') }}</p>
      <p class="text-base text-stone-100">{{ accountStore.me?.login }}</p>
      <p class="text-xs text-stone-500">{{ t('profile.loginImmutableHint') }}</p>
    </section>

    <section class="space-y-2">
      <p class="text-xs uppercase tracking-wide text-stone-500">{{ t('profile.displayName') }}</p>
      <div v-if="!editingName" class="flex items-center gap-2">
        <p class="text-base text-stone-100">{{ accountStore.me?.displayName }}</p>
        <button type="button" class="text-xs text-stone-500 hover:text-stone-300" @click="startEditingName">{{ t('profile.modify') }}</button>
      </div>
      <div v-else class="space-y-2">
        <input
          v-model="nameDraft"
          type="text"
          minlength="2"
          maxlength="32"
          class="w-full rounded bg-stone-800 px-2 py-1 text-sm text-stone-100"
          @keyup.enter="saveName"
        />
        <p v-if="nameError" class="text-xs text-red-400">{{ nameError }}</p>
        <div class="flex gap-2 text-xs">
          <button type="button" class="rounded bg-emerald-600 px-2 py-1 font-semibold hover:bg-emerald-500" :disabled="savingName" @click="saveName">
            {{ t('profile.save') }}
          </button>
          <button type="button" class="text-stone-500 hover:text-stone-300" @click="editingName = false">{{ t('common.cancel') }}</button>
        </div>
      </div>
    </section>

    <section class="space-y-2">
      <p class="text-xs uppercase tracking-wide text-stone-500">{{ t('profile.bio') }}</p>
      <div v-if="!editingBio" class="space-y-2">
        <p class="whitespace-pre-wrap text-sm text-stone-300">{{ accountStore.me?.bio || t('profile.bioEmpty') }}</p>
        <button type="button" class="text-xs text-stone-500 hover:text-stone-300" @click="startEditingBio">{{ t('profile.modify') }}</button>
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
        <div class="flex gap-2 text-xs">
          <button type="button" class="rounded bg-emerald-600 px-2 py-1 font-semibold hover:bg-emerald-500" :disabled="savingBio" @click="saveBio">
            {{ t('profile.save') }}
          </button>
          <button type="button" class="text-stone-500 hover:text-stone-300" @click="editingBio = false">{{ t('common.cancel') }}</button>
        </div>
      </div>
    </section>
  </div>
</template>
