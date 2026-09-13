<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AppHeader from './components/AppHeader.vue'
import { useAuthStore } from './stores/auth'
import { useSettingsStore } from './stores/settings'

const { t } = useI18n()
const route = useRoute()
const authStore = useAuthStore()
const settingsStore = useSettingsStore()

/**
 * Écran de partie sur mobile (<67rem, même seuil que le layout @container de
 * GameView.vue) : page à hauteur fixe sans scroll (glisser un doigt ne doit
 * jamais hésiter entre "scroller" et "déplacer une pièce"). Le footer
 * (crédits, peu utile en jeu) disparaît pour laisser toute la hauteur à
 * l'échiquier ; le header reste (sert aussi de lien retour accueil via le
 * logo, la flèche dédiée ayant été retirée de GameView).
 */
const isGameRoute = computed(() => route.name === 'game')

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
  <div class="flex min-h-screen flex-col" :class="isGameRoute ? 'max-[67rem]:h-[100dvh] max-[67rem]:overflow-hidden' : ''">
    <AppHeader />
    <router-view class="min-h-0 flex-1" />
    <footer
      class="flex-col items-center gap-1 py-4 text-center text-xs text-stone-500"
      :class="isGameRoute ? 'hidden min-[67rem]:flex' : 'flex'"
    >
      <span>{{ t('footer.piecesCredit') }}</span>
      <router-link to="/credits" class="underline hover:text-stone-300">{{ t('footer.creditsLink') }}</router-link>
    </footer>
  </div>
</template>
