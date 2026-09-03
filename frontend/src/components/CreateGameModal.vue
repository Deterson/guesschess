<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Color } from '../types/api'

defineProps<{
  open: boolean
}>()
const emit = defineEmits<{
  confirm: [{ color: Color | 'RANDOM'; noGuessmate: boolean }]
  close: []
}>()

const { t } = useI18n()
const color = ref<Color | 'RANDOM'>('RANDOM')
const noGuessmate = ref(false)

function confirm() {
  emit('confirm', { color: color.value, noGuessmate: noGuessmate.value })
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-10 flex items-center justify-center bg-black/60">
    <div class="w-full max-w-sm space-y-4 rounded-lg bg-stone-800 p-6 text-left shadow-xl">
      <p class="text-center font-semibold text-stone-200">{{ t('home.createModalTitle') }}</p>

      <fieldset class="w-full rounded-lg bg-stone-900 px-4 py-3">
        <legend class="px-1 text-sm font-semibold">{{ t('home.colorLegend') }}</legend>
        <div class="flex gap-4 text-sm text-stone-300">
          <label class="flex items-center gap-2">
            <input type="radio" v-model="color" value="WHITE" class="accent-emerald-600" />
            {{ t('common.white') }}
          </label>
          <label class="flex items-center gap-2">
            <input type="radio" v-model="color" value="BLACK" class="accent-emerald-600" />
            {{ t('common.black') }}
          </label>
          <label class="flex items-center gap-2">
            <input type="radio" v-model="color" value="RANDOM" class="accent-emerald-600" />
            {{ t('home.random') }}
          </label>
        </div>
      </fieldset>

      <!-- Variante sans Guessmate désactivée pour l'instant, à remettre plus tard.
      <label class="flex items-center gap-3 rounded-lg bg-stone-900 px-4 py-3 text-sm">
        <input type="checkbox" v-model="noGuessmate" class="h-4 w-4 accent-emerald-600" />
        <i18n-t keypath="home.noGuessmateTitle" tag="span" class="text-left font-semibold">
          <template #link>
            <router-link
              to="/how-to-play#guessmate"
              class="text-violet-400 underline hover:text-violet-300"
              @click="emit('close')"
            >
              {{ t('gameStatusBar.guessmateLinkText') }}
            </router-link>
          </template>
        </i18n-t>
      </label>
      -->

      <div class="flex gap-2">
        <button
          type="button"
          class="flex-1 rounded-lg bg-emerald-600 px-4 py-2 font-semibold hover:bg-emerald-500"
          @click="confirm"
        >
          {{ t('home.createButton') }}
        </button>
        <button
          type="button"
          class="flex-1 rounded-lg bg-stone-700 px-4 py-2 text-stone-300 hover:bg-stone-600"
          @click="emit('close')"
        >
          {{ t('common.cancel') }}
        </button>
      </div>
    </div>
  </div>
</template>
