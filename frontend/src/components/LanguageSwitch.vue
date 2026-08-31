<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setLocale } from '../i18n'

const { locale } = useI18n()
</script>

<template>
  <div class="flex items-center gap-1.5">
    <button
      type="button"
      class="text-lg leading-none transition-opacity"
      :class="locale === 'fr' ? 'opacity-100' : 'opacity-40 hover:opacity-75'"
      title="Français"
      aria-label="Français"
      @click="setLocale('fr')"
    >
      🇫🇷
    </button>
    <button
      type="button"
      class="text-lg leading-none transition-opacity"
      :class="locale === 'en' ? 'opacity-100' : 'opacity-40 hover:opacity-75'"
      title="English"
      aria-label="English"
      @click="setLocale('en')"
    >
      🇬🇧
    </button>
  </div>
</template>
