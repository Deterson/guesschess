<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-1 flex-col items-center justify-center gap-6 px-4 py-12 text-center text-sm text-stone-500">
    <div>
      <p class="text-lg text-stone-400 underline">{{ t('credits.creator') }}</p>
      <a
        href="https://lichess.org/@/Deterson"
        target="_blank"
        rel="noopener noreferrer"
        class="text-stone-400 underline hover:text-stone-200"
      >Deterson</a>
    </div>
    <div>
      <p class="text-lg text-stone-400 underline">{{ t('credits.inventor') }}</p>
      <p>DelsinR</p>
    </div>
    <p class="mt-8 text-xs text-stone-600">{{ t('credits.thanks') }}</p>
  </div>
</template>
