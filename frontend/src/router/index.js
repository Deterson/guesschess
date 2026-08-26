import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import GameView from '../views/GameView.vue'
import JoinView from '../views/JoinView.vue'
import OAuthCallbackView from '../views/OAuthCallbackView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    {
      path: '/game/:gameId',
      name: 'game',
      component: GameView,
      props: (route) => ({
        gameId: route.params.gameId,
        token: route.query.token,
        color: route.query.color,
        inviteToken: route.query.inviteToken,
        inviteColor: route.query.inviteColor,
      }),
    },
    {
      path: '/join/:gameId',
      name: 'join',
      component: JoinView,
      props: (route) => ({
        gameId: route.params.gameId,
        token: route.query.token,
        color: route.query.color,
      }),
    },
    { path: '/oauth-callback', name: 'oauth-callback', component: OAuthCallbackView },
  ],
})
