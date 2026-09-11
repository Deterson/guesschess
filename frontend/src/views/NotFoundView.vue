<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t, locale } = useI18n()

const randomWikipediaUrl = computed(() =>
  locale.value === 'en'
    ? 'https://en.wikipedia.org/wiki/Special:Random'
    : 'https://fr.wikipedia.org/wiki/Sp%C3%A9cial:Page_au_hasard',
)
</script>

<template>
  <div class="flex flex-1 flex-col items-center justify-center gap-6 px-4 py-12 text-center">
    <h1 class="text-lg text-stone-400">{{ t('notFound.title') }}</h1>
    <div class="flex flex-col items-center gap-3">
      <router-link to="/" class="rounded-lg bg-stone-700 px-6 py-3 font-semibold hover:bg-stone-600">
        {{ t('notFound.backHome') }}
      </router-link>
      <a
        :href="randomWikipediaUrl"
        target="_blank"
        rel="noopener noreferrer"
        class="rounded-lg bg-stone-700 px-6 py-3 font-semibold hover:bg-stone-600"
      >{{ t('notFound.randomWikipedia') }}</a>
    </div>
  </div>
</template>
