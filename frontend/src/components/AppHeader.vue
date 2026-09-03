<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import LoginModal from './LoginModal.vue'
import LanguageSwitch from './LanguageSwitch.vue'
import ThemeToggle from './ThemeToggle.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const showLoginModal = ref(false)
const inGame = computed(() => route.name === 'game')

function logout() {
  authStore.logout()
  router.push('/')
}
</script>

<template>
  <header class="flex items-center justify-between gap-4 border-b border-stone-800 px-4 py-3">
    <div class="flex items-center gap-6">
      <router-link to="/" class="flex items-center">
        <img src="/guesschess-minimal.png" alt="Guesschess" class="h-8 w-8" />
      </router-link>

      <nav class="flex items-center gap-4 text-sm font-semibold text-stone-300">
        <router-link to="/" class="hover:text-white">{{ t('header.play') }}</router-link>
        <router-link to="/how-to-play" class="hover:text-white">{{ t('header.tutorial') }}</router-link>
      </nav>
    </div>

    <div class="flex items-center gap-4">
      <template v-if="authStore.isLoggedIn">
        <router-link to="/my-profile" class="text-sm font-semibold text-stone-300 hover:text-white">
          {{ t('header.profile') }}
        </router-link>
        <button type="button" class="cursor-pointer text-sm font-semibold text-stone-300 hover:text-white" @click="logout">
          {{ t('header.logout') }}
        </button>
      </template>
      <button v-else type="button" class="cursor-pointer text-sm font-semibold text-stone-300 hover:text-white" @click="showLoginModal = true">
        {{ t('header.login') }}
      </button>

      <ThemeToggle />
      <LanguageSwitch />
    </div>
  </header>

  <LoginModal :open="showLoginModal" :return-to="route.fullPath" :in-game="inGame" @close="showLoginModal = false" />
</template>
