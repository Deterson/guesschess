<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale, SUPPORTED_LOCALES, type Locale } from '../i18n'
import FlagFr from './FlagFr.vue'
import FlagGb from './FlagGb.vue'

const { locale } = useI18n()
const isOpen = ref(false)
const rootRef = ref<HTMLElement | null>(null)

const FLAGS: Record<Locale, typeof FlagFr> = {
  fr: FlagFr,
  en: FlagGb,
}

function toggle() {
  isOpen.value = !isOpen.value
}

function choose(value: Locale) {
  setLocale(value)
  isOpen.value = false
}

function onClickOutside(event: MouseEvent) {
  if (rootRef.value && !rootRef.value.contains(event.target as Node)) {
    isOpen.value = false
  }
}

onMounted(() => document.addEventListener('mousedown', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('mousedown', onClickOutside))
</script>

<template>
  <div ref="rootRef" class="relative">
    <button
      type="button"
      class="flex cursor-pointer items-center rounded p-1 opacity-80 transition-opacity hover:opacity-100"
      :aria-expanded="isOpen"
      @click="toggle"
    >
      <component :is="FLAGS[locale as Locale]" class="h-4 w-6 shrink-0 rounded-[2px]" />
    </button>

    <ul
      v-if="isOpen"
      class="absolute right-0 top-full z-10 mt-1 rounded border border-stone-700 bg-stone-900 p-1 shadow-lg"
    >
      <li v-for="value in SUPPORTED_LOCALES" :key="value">
        <button
          type="button"
          class="flex cursor-pointer items-center rounded p-1 opacity-70 transition-opacity hover:opacity-100"
          :class="{ 'opacity-100': locale === value }"
          @click="choose(value)"
        >
          <component :is="FLAGS[value]" class="h-4 w-6 shrink-0 rounded-[2px]" />
        </button>
      </li>
    </ul>
  </div>
</template>
