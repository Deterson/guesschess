<script setup lang="ts">
import AppHeader from './components/AppHeader.vue'
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader />
    <router-view class="flex-1" />
    <footer class="py-4 text-center text-xs text-stone-500">
      Pièces d'échecs : Cburnett, CC BY-SA 3.0
    </footer>
  </div>
</template>
