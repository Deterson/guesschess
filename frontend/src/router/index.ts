import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import GameView from '../views/GameView.vue'
import OAuthCallbackView from '../views/OAuthCallbackView.vue'
import ChooseLoginView from '../views/ChooseLoginView.vue'
import HowToPlayView from '../views/HowToPlayView.vue'
import ProfileLayout from '../views/ProfileLayout.vue'
import ProfileGamesView from '../views/ProfileGamesView.vue'
import ProfileSettingsView from '../views/ProfileSettingsView.vue'
import ProfileAboutView from '../views/ProfileAboutView.vue'
import PublicProfileView from '../views/PublicProfileView.vue'
import { useAuthStore } from '../stores/auth'
import { useAccountStore } from '../stores/account'
import { peek as peekPendingRegistration } from '../services/pendingRegistration'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to) {
    if (to.hash) {
      return { el: to.hash, behavior: 'smooth' }
    }
    return { top: 0 }
  },
  routes: [
    { path: '/', name: 'home', component: HomeView },
    {
      path: '/game/:gameId',
      name: 'game',
      component: GameView,
      props: (route) => ({ gameId: route.params.gameId }),
    },
    { path: '/oauth-callback', name: 'oauth-callback', component: OAuthCallbackView },
    { path: '/choose-login', name: 'choose-login', component: ChooseLoginView },
    { path: '/how-to-play', name: 'how-to-play', component: HowToPlayView },
    {
      path: '/my-profile',
      component: ProfileLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: { name: 'profile-about' } },
        { path: 'about', name: 'profile-about', component: ProfileAboutView },
        { path: 'games', name: 'profile-games', component: ProfileGamesView },
        { path: 'settings', name: 'profile-settings', component: ProfileSettingsView },
      ],
    },
    {
      path: '/profile/:login',
      name: 'public-profile',
      component: PublicProfileView,
      props: (route) => ({ login: route.params.login }),
    },
  ],
})

/**
 * Page de profil reservee aux comptes connectes (etape 8) - un joueur anonyme n'y a
 * pas acces, meme via un lien direct. Le backend applique la meme regle
 * independamment (/api/account/** exige un JWT, voir SecurityConfig) : ce garde n'est
 * qu'un confort d'UX, jamais le seul rempart.
 *
 * Depuis l'etape 14 : un compte connecte dont le login n'est pas encore pose
 * (accountStore.needsLogin - toujours un compte historique, voir stores/account.ts)
 * est bloque sur /choose-login avant d'aller nulle part ailleurs, `redirect` portant
 * la destination visee pour y revenir une fois le login pose (voir ChooseLoginView).
 * oauth-callback et choose-login eux-memes ne sont jamais interceptes : c'est
 * justement le chemin qui menent a resoudre cet etat.
 */
router.beforeEach(async (to) => {
  if (to.name === 'oauth-callback' || to.name === 'choose-login') return

  const authStore = useAuthStore()
  const accountStore = useAccountStore()

  if (!authStore.isLoggedIn) {
    // Une deconnexion (explicite, ou jeton perime rejete par le backend - voir
    // services/api.ts) doit effacer la photo du compte precedent : sinon une
    // reconnexion sous un autre compte reutiliserait needsLogin/me perimes tant que
    // cette meme session ne recharge pas la page.
    if (accountStore.loaded) accountStore.reset()
    if (peekPendingRegistration() !== null) {
      return { name: 'choose-login' }
    }
    if (to.meta.requiresAuth) {
      return { name: 'home' }
    }
    return
  }

  if (!accountStore.loaded) {
    await accountStore.load(authStore.token!).catch(() => {})
  }
  if (accountStore.needsLogin) {
    return { name: 'choose-login', query: { redirect: to.fullPath } }
  }
})

export default router
