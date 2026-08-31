<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="flex min-h-[60vh] items-center justify-center">
    <p class="text-sm text-stone-500">{{ t('howToPlay.placeholder') }}</p>
  </div>
</template>
