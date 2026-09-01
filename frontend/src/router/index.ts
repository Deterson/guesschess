import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import GameView from '../views/GameView.vue'
import OAuthCallbackView from '../views/OAuthCallbackView.vue'
import HowToPlayView from '../views/HowToPlayView.vue'
import ProfileLayout from '../views/ProfileLayout.vue'
import ProfileGamesView from '../views/ProfileGamesView.vue'
import { useAuthStore } from '../stores/auth'

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
    { path: '/how-to-play', name: 'how-to-play', component: HowToPlayView },
    {
      path: '/profile',
      component: ProfileLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: { name: 'profile-games' } },
        { path: 'games', name: 'profile-games', component: ProfileGamesView },
      ],
    },
  ],
})

/**
 * Page de profil reservee aux comptes connectes (etape 8) - un joueur anonyme n'y a
 * pas acces, meme via un lien direct. Le backend applique la meme regle
 * independamment (/api/account/** exige un JWT, voir SecurityConfig) : ce garde n'est
 * qu'un confort d'UX, jamais le seul rempart.
 */
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !useAuthStore().isLoggedIn) {
    return { name: 'home' }
  }
})

export default router
