<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import AppHeader from './components/AppHeader.vue'
import { useAuthStore } from './stores/auth'
import { useSettingsStore } from './stores/settings'

const { t } = useI18n()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()

/**
 * Precharge une seule fois les parametres du compte (etape "Paramètres" du profil) des
 * l'arrivee sur l'app si l'utilisateur est deja connecte - sinon GameStatusBar
 * afficherait la valeur par defaut jusqu'a une visite de /my-profile/settings. Echec
 * silencieux : un parametre non charge retombe sur son defaut (comportement actuel).
 */
onMounted(() => {
  if (authStore.isLoggedIn) settingsStore.load(authStore.token!).catch(() => {})
})
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
