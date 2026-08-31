<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import AppHeader from './components/AppHeader.vue'

const { t } = useI18n()
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader />
    <router-view class="flex-1" />
    <footer class="py-4 text-center text-xs text-stone-500">
      {{ t('footer.piecesCredit') }}
    </footer>
  </div>
</template>
